#!/usr/bin/env python3
"""Exercise actual Nginx video delivery with an isolated mock authorization API.

Uses existing local images only; never starts the application Compose stack.
Requires Docker and Python 3. Run: python3 scripts/test-video-proxy.py
This verifies proxy behavior, not Spring authorization or browser codec support.
"""
import http.client
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
BACKEND = r'''
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib, json
from urllib.parse import urlparse, parse_qs

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/test/stream":
            if parse_qs(parsed.query).get("token") != ["valid-secret-query"]:
                self.send_response(403)
                self.end_headers()
                return
            self.send_response(200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("X-Accel-Redirect", "/_protected_videos/fixture.mp4")
            self.end_headers()
        else:
            self.reply({"host": self.headers.get("Host"),
                        "proto": self.headers.get("X-Forwarded-Proto"),
                        "accel": self.headers.get("X-Accel-Supported")})
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.reply({"path": self.path, "bytes": len(body),
                    "sha256": hashlib.sha256(body).hexdigest(),
                    "host": self.headers.get("Host"),
                    "proto": self.headers.get("X-Forwarded-Proto")})
    def reply(self, value):
        body = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *args):
        pass

ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
'''


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, stderr=subprocess.STDOUT).strip()


def require(condition, message):
    if not condition:
        raise AssertionError(message)
    print("PASS " + message, flush=True)


def main():
    import hashlib

    # Inspect first so missing images fail without attempting network pulls.
    for image in ("cbs_elearning-frontend:latest", "cbs_elearning-rag-service:latest"):
        docker("image", "inspect", image)
    prefix = "video-proxy-test-" + uuid.uuid4().hex[:12]
    network, backend, frontend = prefix + "-net", prefix + "-api", prefix + "-nginx"
    created = []
    network_created = False
    with tempfile.TemporaryDirectory(prefix=prefix + "-", dir="/private/tmp") as directory:
        fixture = Path(directory)
        (fixture / "videos").mkdir()
        payload = bytes(range(256)) * 16
        (fixture / "videos" / "fixture.mp4").write_bytes(payload)
        (fixture / "backend.py").write_text(BACKEND)
        try:
            docker("network", "create", network)
            network_created = True
            docker("run", "-d", "--pull=never", "--name", backend,
                   "--network", network, "--network-alias", "backend",
                   "-v", f"{fixture}/backend.py:/tmp/backend.py:ro",
                   "--entrypoint", "python", "cbs_elearning-rag-service:latest",
                   "/tmp/backend.py")
            created.append(backend)
            docker("run", "-d", "--pull=never", "--name", frontend,
                   "--network", network, "-p", "127.0.0.1::80",
                   "-v", f"{ROOT}/CoreTraining/nginx.conf:/etc/nginx/conf.d/default.conf:ro",
                   "-v", f"{fixture}/videos:/srv/uploads/videos:ro",
                   "cbs_elearning-frontend:latest")
            created.append(frontend)
            mapping = json.loads(docker("inspect", frontend))[0]["NetworkSettings"]["Ports"]["80/tcp"][0]
            port = int(mapping["HostPort"])

            def request(path, method="GET", body=None, headers=None):
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
                try:
                    connection.request(method, path, body=body, headers=headers or {})
                    response = connection.getresponse()
                    return response.status, dict(response.getheaders()), response.read()
                finally:
                    connection.close()

            for _ in range(40):
                try:
                    if request("/api/test/headers")[0] == 200:
                        break
                except (OSError, http.client.HTTPException):
                    pass
                time.sleep(0.25)
            else:
                raise RuntimeError("Isolated Nginx/backend did not become ready")

            status, headers, body = request("/api/test/stream?token=valid-secret-query")
            require(status == 200 and body == payload, "authorized stream delivers exact full file")
            require("no-store" in headers.get("Cache-Control", ""), "video response prevents cache storage")
            status, headers, body = request("/api/test/stream?token=valid-secret-query", headers={"Range": "bytes=123-345"})
            require(status == 206 and body == payload[123:346], "Range request returns exact partial bytes with 206")
            require(headers.get("Content-Range") == f"bytes 123-345/{len(payload)}", "Content-Range describes selected bytes")
            require(request("/api/test/stream?token=invalid-secret-query")[0] == 403, "invalid token is denied by upstream")
            require(request("/uploads/videos/fixture.mp4")[0] == 403, "public upload video URL is forbidden")
            require(request("/_protected_videos/fixture.mp4")[0] == 404, "internal video location denies direct access")
            forwarded = {"Host": "elearning.example.test:8443", "X-Forwarded-Proto": "https"}
            status, _, body = request("/api/test/headers", headers=forwarded)
            value = json.loads(body)
            require(status == 200 and value["host"] == forwarded["Host"] and value["proto"] == "https", "HTTPS and Host including port reach backend")
            require(value["accel"] == "true", "API advertises X-Accel support")
            upload = b"--fixture\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.mp4\"\r\n\r\n" + payload + b"\r\n--fixture--\r\n"
            status, _, body = request("/api/admin/videos/upload", method="POST", body=upload,
                                      headers={**forwarded, "Content-Type": "multipart/form-data; boundary=fixture"})
            value = json.loads(body)
            require(status == 200 and value["path"] == "/api/admin/videos/upload" and value["bytes"] == len(upload)
                    and value["sha256"] == hashlib.sha256(upload).hexdigest(), "exact upload endpoint forwards complete multipart body")
            require(value["host"] == forwarded["Host"] and value["proto"] == "https", "upload preserves forwarded HTTPS and Host")
            logs = docker("logs", frontend)
            require("/api/test/stream HTTP" in logs and "secret-query" not in logs,
                    "access logs retain stream path without signed token query")
            for files in (("docker-compose.yml",), ("docker-compose.yml", "docker-compose.production.yml")):
                command = ["compose", "--project-directory", str(ROOT), "--env-file", str(ROOT / ".env.production")]
                for file in files:
                    command.extend(("-f", str(ROOT / file)))
                docker(*command, "config", "--quiet")
                require(True, "Compose configuration valid: " + " + ".join(files))
        except Exception:
            for name in created:
                print("Diagnostic logs for " + name, flush=True)
                print(docker("logs", name), flush=True)
            raise
        finally:
            for name in reversed(created):
                subprocess.run(["docker", "rm", "-f", name], check=False, capture_output=True)
            if network_created:
                subprocess.run(["docker", "network", "rm", network], check=False, capture_output=True)


if __name__ == "__main__":
    main()
