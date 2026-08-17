import os
import unittest
from unittest.mock import patch

from kod_worker import MAX_OUTPUT, Config, DockerSandbox


class DockerSandboxSecurityTests(unittest.TestCase):
    def test_paths_are_confined_to_workspace(self):
        self.assertEqual(DockerSandbox.safe_path("."), ".")
        self.assertEqual(DockerSandbox.safe_path("src/main.py"), "src/main.py")
        for unsafe in ("/etc/passwd", "../secret", "src/../../secret", r"..\secret"):
            with self.subTest(path=unsafe), self.assertRaises(RuntimeError):
                DockerSandbox.safe_path(unsafe)

    def test_workspace_names_require_uuid(self):
        value = "6bfa4f9d-9aa4-4a16-87f3-5b8560637fd6"
        self.assertEqual(DockerSandbox.container_name(DockerSandbox.workspace_id(value)), "kod-sandbox-" + value)
        for unsafe in ("../../host", "", "workspace;docker rm"):
            with self.subTest(value=unsafe), self.assertRaises(RuntimeError):
                DockerSandbox.workspace_id(unsafe)

    def test_default_policy_has_no_network_or_gpu(self):
        with patch.dict(os.environ, {}, clear=True):
            config = Config.load()
        self.assertEqual(config.network, "none")
        self.assertEqual(config.gpus, "")

    def test_account_purge_does_not_recreate_container(self):
        with patch.dict(os.environ, {}, clear=True):
            sandbox = DockerSandbox(Config.load(), api=None)
        workspace_id = "6bfa4f9d-9aa4-4a16-87f3-5b8560637fd6"
        with patch.object(sandbox, "ensure_container") as ensure, patch.object(sandbox, "purge") as purge:
            self.assertEqual(sandbox.execute({"workspaceId": workspace_id, "kind": "purge", "params": {}}), {})
        ensure.assert_not_called()
        purge.assert_called_once_with(workspace_id)

    def test_stream_output_is_bounded(self):
        output = bytearray(b"existing")
        admitted, truncated = DockerSandbox.bounded_output(
            output, b"x" * (MAX_OUTPUT + 100)
        )
        self.assertEqual(len(output), MAX_OUTPUT)
        self.assertEqual(len(admitted), MAX_OUTPUT - len(b"existing"))
        self.assertTrue(truncated)


if __name__ == "__main__":
    unittest.main()
