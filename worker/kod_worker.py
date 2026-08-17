#!/usr/bin/env python3
"""KOD cloud sandbox worker.

The worker never runs a user command on the host. Every operation is executed in
a dedicated, resource-limited Docker container whose only writable persistent
mount is its own named volume at /workspace.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import selectors
import shlex
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


MAX_OUTPUT = 1_000_000
MAX_STREAM_CHUNK = 32_768
WORKSPACE_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


class ApiError(RuntimeError):
    pass


class Cancelled(RuntimeError):
    pass


@dataclass(frozen=True)
class Config:
    api_base: str
    token_file: pathlib.Path
    name: str
    image: str
    memory: str
    cpus: str
    pids: str
    network: str
    gpus: str
    poll_seconds: float
    control_grace_seconds: float

    @staticmethod
    def load() -> "Config":
        return Config(
            api_base=os.environ.get("KOD_API_BASE", "https://kod.kai.com").rstrip("/"),
            token_file=pathlib.Path(os.environ.get("KOD_WORKER_TOKEN_FILE", "./.kod-worker-token")),
            name=os.environ.get("KOD_WORKER_NAME", socket.gethostname()),
            image=os.environ.get("KOD_SANDBOX_IMAGE", "ubuntu:24.04"),
            memory=os.environ.get("KOD_SANDBOX_MEMORY", "8g"),
            cpus=os.environ.get("KOD_SANDBOX_CPUS", "4"),
            pids=os.environ.get("KOD_SANDBOX_PIDS", "512"),
            network=os.environ.get("KOD_SANDBOX_NETWORK", "none"),
            gpus=os.environ.get("KOD_SANDBOX_GPUS", ""),
            poll_seconds=float(os.environ.get("KOD_WORKER_POLL_SECONDS", "1")),
            control_grace_seconds=float(os.environ.get("KOD_WORKER_CONTROL_GRACE_SECONDS", "45")),
        )


class Api:
    def __init__(self, config: Config, token: str | None = None):
        self.base = config.api_base
        self.token = token

    def call(self, method: str, path: str, body: Any | None = None, headers: dict[str, str] | None = None) -> Any:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request_headers = {"Accept": "application/json", **(headers or {})}
        if data is not None:
            request_headers["Content-Type"] = "application/json"
        if self.token:
            request_headers["X-KOD-Worker-Token"] = self.token
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=request_headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            raise ApiError(str(error)) from error
        if payload.get("code") != 0:
            raise ApiError(str(payload.get("message") or "control plane rejected request"))
        return payload.get("data")


class DockerSandbox:
    def __init__(self, config: Config, api: Api):
        self.config = config
        self.api = api

    def execute(self, operation: dict[str, Any]) -> dict[str, Any]:
        workspace_id = self.workspace_id(operation["workspaceId"])
        kind = operation["kind"]
        params = operation.get("params") or {}
        if kind == "purge":
            self.purge(workspace_id)
            return {}
        self.ensure_container(workspace_id)
        if kind == "exec":
            timeout_ms = max(1_000, min(int(params.get("timeout", 120_000)), 300_000))
            return self.shell(workspace_id, str(params.get("command", "")), operation["operationId"], timeout_ms)
        if kind == "read":
            return {"content": self.read(workspace_id, self.safe_path(params.get("filePath")))}
        if kind == "write":
            path = self.safe_path(params.get("filePath"))
            self.write(workspace_id, path, str(params.get("content", "")))
            return {}
        if kind == "edit":
            path = self.safe_path(params.get("filePath"))
            content = self.read(workspace_id, path)
            search = str(params.get("search", ""))
            if not search or search not in content:
                raise RuntimeError("edit search text was not found")
            self.write(workspace_id, path, content.replace(search, str(params.get("replace", "")), 1))
            return {}
        if kind == "ls":
            path = self.safe_path(params.get("dirPath", "."))
            return {"content": self.simple(workspace_id, f"find {shlex.quote(path)} -mindepth 1 -maxdepth 1 -printf '%f\\n' | sort")}
        if kind == "grep":
            path = self.safe_path(params.get("dirPath", "."))
            pattern = str(params.get("pattern", ""))
            include = str(params.get("include", ""))
            include_arg = f" --include={shlex.quote(include)}" if include and self.safe_glob(include) else ""
            command = f"grep -RIn --binary-files=without-match{include_arg} -- {shlex.quote(pattern)} {shlex.quote(path)}"
            return {"content": self.simple(workspace_id, command, allow_one=True)}
        if kind == "find":
            path = self.safe_path(params.get("dirPath", "."))
            pattern = str(params.get("pattern") or "*")
            self.safe_glob(pattern)
            return {"content": self.simple(workspace_id, f"find {shlex.quote(path)} -name {shlex.quote(pattern)} -print")}
        if kind == "reset":
            self.reset(workspace_id)
            self.ensure_container(workspace_id)
            return {}
        raise RuntimeError(f"unsupported operation: {kind}")

    def ensure_container(self, workspace_id: str) -> None:
        container = self.container_name(workspace_id)
        inspect = subprocess.run(["docker", "inspect", "-f", "{{.State.Running}}", container], capture_output=True, text=True)
        if inspect.returncode == 0 and inspect.stdout.strip() == "true":
            return
        if inspect.returncode == 0:
            subprocess.run(["docker", "rm", "-f", container], capture_output=True, check=False)
        volume = self.volume_name(workspace_id)
        subprocess.run(["docker", "volume", "create", volume], capture_output=True, text=True, check=True)
        subprocess.run([
            "docker", "run", "--rm", "--network", "none", "--cap-drop", "ALL",
            "-v", f"{volume}:/workspace", self.config.image,
            "sh", "-lc", "chown 65532:65532 /workspace",
        ], capture_output=True, text=True, check=True)
        command = [
            "docker", "run", "-d", "--name", container,
            "--user", "65532:65532", "--workdir", "/workspace", "--read-only",
            "--tmpfs", "/tmp:rw,noexec,nosuid,size=256m", "--security-opt", "no-new-privileges",
            "--cap-drop", "ALL", "--pids-limit", self.config.pids,
            "--memory", self.config.memory, "--cpus", self.config.cpus,
            "--network", self.config.network, "-v", f"{volume}:/workspace",
        ]
        if self.config.gpus:
            command += ["--gpus", self.config.gpus]
        command += [self.config.image, "sleep", "infinity"]
        subprocess.run(command, capture_output=True, text=True, check=True)

    def shell(self, workspace_id: str, command: str, operation_id: str, timeout_ms: int) -> dict[str, Any]:
        if not command.strip():
            raise RuntimeError("command must not be empty")
        process = subprocess.Popen(
            ["docker", "exec", self.container_name(workspace_id), "sh", "-lc", command],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            bufsize=0,
        )
        deadline = time.monotonic() + timeout_ms / 1000
        next_cancellation_check = 0.0
        next_heartbeat = 0.0
        last_control_success = time.monotonic()
        outputs = {"stdout": bytearray(), "stderr": bytearray()}
        truncation_reported = False
        selector = selectors.DefaultSelector()
        assert process.stdout is not None and process.stderr is not None
        for name, stream in (("stdout", process.stdout), ("stderr", process.stderr)):
            os.set_blocking(stream.fileno(), False)
            selector.register(stream, selectors.EVENT_READ, name)
        try:
            while selector.get_map() or process.poll() is None:
                now = time.monotonic()
                if now >= deadline:
                    self.stop_container(workspace_id)
                    raise RuntimeError("operation timed out")
                if now >= next_heartbeat:
                    try:
                        self.api.call("POST", "/api/cloud-sandbox/workers/heartbeat")
                        last_control_success = now
                    except ApiError:
                        if now - last_control_success >= self.config.control_grace_seconds:
                            self.stop_container(workspace_id)
                            raise RuntimeError("control plane unavailable during operation")
                    next_heartbeat = now + 20
                if now >= next_cancellation_check:
                    try:
                        cancellation = self.api.call(
                            "GET", f"/api/cloud-sandbox/workers/operations/{operation_id}/cancellation"
                        )
                        last_control_success = now
                        if cancellation.get("cancelRequested"):
                            self.stop_container(workspace_id)
                            raise Cancelled("operation cancelled")
                    except ApiError:
                        if now - last_control_success >= self.config.control_grace_seconds:
                            self.stop_container(workspace_id)
                            raise RuntimeError("control plane unavailable during operation")
                    next_cancellation_check = now + 1
                for key, _ in selector.select(timeout=0.25):
                    data = os.read(key.fileobj.fileno(), 65_536)
                    if not data:
                        selector.unregister(key.fileobj)
                        continue
                    admitted, truncated = self.bounded_output(outputs[key.data], data)
                    if admitted:
                        self.emit_output(operation_id, key.data, admitted)
                    if truncated and not truncation_reported:
                        self.emit_event(operation_id, "output_truncated", {"maxBytesPerStream": MAX_OUTPUT})
                        truncation_reported = True
            process.wait(timeout=5)
        finally:
            selector.close()
        return {
            "stdout": bytes(outputs["stdout"]).decode("utf-8", errors="replace"),
            "stderr": bytes(outputs["stderr"]).decode("utf-8", errors="replace"),
            "exitCode": process.returncode,
            "truncated": truncation_reported,
        }

    @staticmethod
    def bounded_output(buffer: bytearray, data: bytes) -> tuple[bytes, bool]:
        available = max(0, MAX_OUTPUT - len(buffer))
        admitted = data[:available]
        buffer.extend(admitted)
        return admitted, len(admitted) < len(data)

    def emit_output(self, operation_id: str, stream: str, data: bytes) -> None:
        for offset in range(0, len(data), MAX_STREAM_CHUNK):
            chunk = data[offset:offset + MAX_STREAM_CHUNK].decode("utf-8", errors="replace")
            self.emit_event(operation_id, stream, {"chunk": chunk})

    def emit_event(self, operation_id: str, event_type: str, payload: dict[str, Any]) -> None:
        try:
            self.api.call(
                "POST", f"/api/cloud-sandbox/workers/operations/{operation_id}/events",
                {"type": event_type, "payload": payload},
            )
        except ApiError:
            # Streaming is best effort. The bounded final result remains authoritative,
            # and a temporary event failure must not terminate the user's command.
            pass

    def read(self, workspace_id: str, path: str) -> str:
        completed = subprocess.run(
            ["docker", "exec", self.container_name(workspace_id), "cat", "--", path],
            capture_output=True, timeout=30,
        )
        if completed.returncode != 0:
            raise RuntimeError(completed.stderr.decode("utf-8", errors="replace")[:4000])
        return completed.stdout[:MAX_OUTPUT].decode("utf-8", errors="replace")

    def write(self, workspace_id: str, path: str, content: str) -> None:
        parent = str(pathlib.PurePosixPath(path).parent)
        command = f"mkdir -p -- {shlex.quote(parent)} && cat > {shlex.quote(path)}"
        completed = subprocess.run(
            ["docker", "exec", "-i", self.container_name(workspace_id), "sh", "-lc", command],
            input=content.encode("utf-8"), capture_output=True, timeout=30,
        )
        if completed.returncode != 0:
            raise RuntimeError(completed.stderr.decode("utf-8", errors="replace")[:4000])

    def simple(self, workspace_id: str, command: str, allow_one: bool = False) -> str:
        completed = subprocess.run(
            ["docker", "exec", self.container_name(workspace_id), "sh", "-lc", command],
            capture_output=True, timeout=60,
        )
        if completed.returncode != 0 and not (allow_one and completed.returncode == 1):
            raise RuntimeError(completed.stderr.decode("utf-8", errors="replace")[:4000])
        return completed.stdout[:MAX_OUTPUT].decode("utf-8", errors="replace")

    def reset(self, workspace_id: str) -> None:
        self.stop_container(workspace_id)
        subprocess.run(["docker", "volume", "rm", self.volume_name(workspace_id)], capture_output=True, check=False)

    def purge(self, workspace_id: str) -> None:
        """Irreversibly remove an account-deletion workspace without recreating it."""
        self.stop_container(workspace_id)
        completed = subprocess.run(
            ["docker", "volume", "rm", self.volume_name(workspace_id)],
            capture_output=True, text=True, check=False,
        )
        if completed.returncode != 0 and "no such volume" not in completed.stderr.lower():
            raise RuntimeError(completed.stderr.strip()[:4000] or "workspace volume purge failed")

    def stop_container(self, workspace_id: str) -> None:
        subprocess.run(["docker", "rm", "-f", self.container_name(workspace_id)], capture_output=True, check=False)

    @staticmethod
    def safe_path(value: Any) -> str:
        raw = str(value or ".").replace("\\", "/")
        path = pathlib.PurePosixPath(raw)
        if path.is_absolute() or ".." in path.parts:
            raise RuntimeError("path must stay inside /workspace")
        return str(path) if path.parts else "."

    @staticmethod
    def safe_glob(value: str) -> bool:
        if "/" in value or "\\" in value or ".." in value or len(value) > 128:
            raise RuntimeError("invalid file glob")
        return bool(value)

    @staticmethod
    def workspace_id(value: str) -> str:
        value = str(value).lower()
        if not WORKSPACE_RE.fullmatch(value):
            raise RuntimeError("invalid workspace id")
        return value

    @staticmethod
    def container_name(workspace_id: str) -> str:
        return "kod-sandbox-" + workspace_id

    @staticmethod
    def volume_name(workspace_id: str) -> str:
        return "kod-workspace-" + workspace_id


def capabilities(config: Config) -> dict[str, Any]:
    version = subprocess.run(["docker", "version", "--format", "{{.Server.Version}}"], capture_output=True, text=True)
    if version.returncode != 0:
        raise RuntimeError("Docker daemon is unavailable: " + version.stderr.strip())
    return {
        "runtime": "docker",
        "dockerVersion": version.stdout.strip(),
        "sandboxImage": config.image,
        "gpu": bool(config.gpus),
        "network": config.network,
        "operations": ["exec", "read", "write", "edit", "ls", "grep", "find", "reset", "purge"],
    }


def save_token(path: pathlib.Path, token: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write(token)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


def pair(config: Config, code: str) -> None:
    api = Api(config)
    result = api.call("POST", "/api/cloud-sandbox/workers/pair", {
        "code": code,
        "name": config.name,
        "capabilities": capabilities(config),
    })
    save_token(config.token_file, result["workerToken"])
    print(f"Paired worker {result['workerId']}; token stored in {config.token_file}")


def run(config: Config) -> None:
    token = config.token_file.read_text(encoding="utf-8").strip()
    if not token:
        raise RuntimeError("worker token file is empty; pair the worker first")
    api = Api(config, token)
    sandbox = DockerSandbox(config, api)
    caps = capabilities(config)
    last_heartbeat = 0.0
    while True:
        now = time.monotonic()
        if now - last_heartbeat >= 20:
            api.call("POST", "/api/cloud-sandbox/workers/heartbeat", caps)
            last_heartbeat = now
        result = api.call("POST", "/api/cloud-sandbox/workers/claim")
        operation = result.get("operation") or {}
        if not operation:
            time.sleep(config.poll_seconds)
            continue
        operation_id = operation["operationId"]
        api.call("POST", f"/api/cloud-sandbox/workers/operations/{operation_id}/events", {
            "type": "worker_progress", "payload": {"message": "container operation started"},
        })
        try:
            output = sandbox.execute(operation)
            api.call("POST", f"/api/cloud-sandbox/workers/operations/{operation_id}/complete", {
                "success": True, "result": output,
            })
        except Cancelled as error:
            api.call("POST", f"/api/cloud-sandbox/workers/operations/{operation_id}/complete", {
                "success": False, "error": str(error),
            })
        except Exception as error:  # worker must report bounded failure and continue serving
            api.call("POST", f"/api/cloud-sandbox/workers/operations/{operation_id}/complete", {
                "success": False, "error": str(error)[:4000],
            })


def main() -> int:
    parser = argparse.ArgumentParser(description="KOD isolated Docker worker")
    subparsers = parser.add_subparsers(dest="command", required=True)
    pair_parser = subparsers.add_parser("pair", help="exchange a one-time pairing code for a worker token")
    pair_parser.add_argument("--code", required=True)
    subparsers.add_parser("run", help="start heartbeat and task claim loop")
    args = parser.parse_args()
    config = Config.load()
    try:
        pair(config, args.code) if args.command == "pair" else run(config)
        return 0
    except KeyboardInterrupt:
        return 130
    except Exception as error:
        print(f"kod-worker: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
