#!/usr/bin/env python3
"""本机告警演练接收器：提供可切换指标并保存最近的 Alertmanager 通知。"""

from __future__ import annotations

import argparse
import json
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


class DrillState:
    """线程安全地保存演练开关和最近通知。"""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._firing = False
        self._events: list[dict[str, Any]] = []

    def set_firing(self, firing: bool) -> None:
        with self._lock:
            self._firing = firing

    def metric(self) -> int:
        with self._lock:
            return 1 if self._firing else 0

    def add_event(self, payload: dict[str, Any]) -> None:
        event = {
            "receivedAt": datetime.now(timezone.utc).isoformat(),
            "status": payload.get("status"),
            "receiver": payload.get("receiver"),
            "alerts": [
                {
                    "status": alert.get("status"),
                    "labels": alert.get("labels", {}),
                    "startsAt": alert.get("startsAt"),
                    "endsAt": alert.get("endsAt"),
                }
                for alert in payload.get("alerts", [])
            ],
        }
        with self._lock:
            self._events.append(event)
            del self._events[:-100]

    def events(self) -> list[dict[str, Any]]:
        with self._lock:
            return list(self._events)


STATE = DrillState()


class Handler(BaseHTTPRequestHandler):
    """提供 Prometheus 指标、演练开关和 Alertmanager Webhook。"""

    server_version = "AcquiringReliabilityDrill/1.0"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/metrics":
            metric = (
                "# HELP acquiring_reliability_delivery_drill Local alert delivery drill switch.\n"
                "# TYPE acquiring_reliability_delivery_drill gauge\n"
                f"acquiring_reliability_delivery_drill {STATE.metric()}\n"
            )
            self._write(200, metric.encode("utf-8"), "text/plain; version=0.0.4")
            return
        if self.path == "/events":
            self._json(200, {"events": STATE.events()})
            return
        if self.path == "/health":
            self._json(200, {"status": "UP", "firing": bool(STATE.metric())})
            return
        self._json(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path == "/drill/firing":
            STATE.set_firing(True)
            self._json(200, {"firing": True})
            return
        if self.path == "/drill/resolved":
            STATE.set_firing(False)
            self._json(200, {"firing": False})
            return
        if self.path == "/alerts":
            length = int(self.headers.get("Content-Length", "0"))
            try:
                payload = json.loads(self.rfile.read(length) or b"{}")
            except json.JSONDecodeError:
                self._json(400, {"error": "invalid json"})
                return
            STATE.add_event(payload)
            self._json(200, {"accepted": True})
            return
        self._json(404, {"error": "not found"})

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"{self.address_string()} - {fmt % args}", flush=True)

    def _json(self, status: int, payload: dict[str, Any]) -> None:
        self._write(status, json.dumps(payload, ensure_ascii=False).encode("utf-8"), "application/json")

    def _write(self, status: int, payload: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


def main() -> None:
    parser = argparse.ArgumentParser(description="本机可靠性告警演练接收器")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=19093, type=int)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"listening on http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
