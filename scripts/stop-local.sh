#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.run"
BACKEND_PID_FILE="$LOG_DIR/backend.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend.pid"

stop_by_pid_file() {
  local pid_file="$1"
  local name="$2"

  if [[ ! -f "$pid_file" ]]; then
    echo "$name not running (pid file missing)"
    return
  fi

  local pid
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"
    echo "stopped $name pid=$pid"
  else
    echo "$name pid=$pid already stopped"
  fi
  rm -f "$pid_file"
}

stop_by_pid_file "$BACKEND_PID_FILE" "backend"
stop_by_pid_file "$FRONTEND_PID_FILE" "frontend"
