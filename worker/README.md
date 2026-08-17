# KOD cloud sandbox worker

This service turns a Linux bare-metal node into a KOD worker. It requires Python
3.10+ and Docker. User commands only run in dedicated Docker containers; the
worker does not execute them through a host shell.

## Pair and run

1. Enable the control plane with `KOD_CLOUD_SANDBOX_ENABLED=true`, set a long
   random `KOD_WORKER_BOOTSTRAP_SECRET`, and start the backend.
2. An operator creates a ten-minute, one-time code:

   ```sh
   curl -X POST https://kod.kai.com/api/cloud-sandbox/workers/pairing-codes \
     -H "X-KOD-Worker-Bootstrap: $KOD_WORKER_BOOTSTRAP_SECRET"
   ```

3. Copy `.env.example` to `/etc/kod-worker.env`, load it, and pair once:

   ```sh
   set -a; . /etc/kod-worker.env; set +a
   python3 kod_worker.py pair --code CODE_FROM_STEP_2
   ```

4. Install `kod-worker.service`. Restrict `/etc/kod-worker.env` and
   `/var/lib/kod-worker/token` to the service account.

The default sandbox has no network and no GPU. Network or GPU access must be an
explicit worker-pool policy. Production should additionally use a private image
registry, immutable image digests, Docker authorization policy, host monitoring,
and separate nodes/pools for untrusted code and model inference.

Command stdout and stderr are sent to the authenticated control plane as bounded
32 KiB event chunks and replayed over the workspace SSE endpoint. Each stream is
capped at 1 MB per operation; the final result and event history carry an explicit
truncation flag when the cap is reached.

While a command is running, the worker renews its control-plane heartbeat every
20 seconds and stops the container if the control plane remains unreachable for
45 seconds. A lost worker causes the active operation to fail explicitly and the
workspace to enter `worker_lost`; the scheduler never silently mounts an empty
same-named volume on another node. Node-local volumes therefore require the
original node to return for reset/account purge. Production multi-node pools
should use a shared encrypted workspace volume layer before enabling migration.
