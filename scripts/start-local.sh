#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/.run"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
BACKEND_PID_FILE="$LOG_DIR/backend.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend.pid"

mkdir -p "$LOG_DIR"

if [[ -f "$BACKEND_PID_FILE" ]] && kill -0 "$(cat "$BACKEND_PID_FILE")" 2>/dev/null; then
  echo "backend already running, pid=$(cat "$BACKEND_PID_FILE")"
else
  (
    cd "$BACKEND_DIR"
    mvn spring-boot:run >"$BACKEND_LOG" 2>&1 &
    echo $! >"$BACKEND_PID_FILE"
  )
  echo "backend started, pid=$(cat "$BACKEND_PID_FILE"), log=$BACKEND_LOG"
fi

if [[ -f "$FRONTEND_PID_FILE" ]] && kill -0 "$(cat "$FRONTEND_PID_FILE")" 2>/dev/null; then
  echo "frontend already running, pid=$(cat "$FRONTEND_PID_FILE")"
else
  (
    cd "$FRONTEND_DIR"
    pnpm install >/dev/null
    pnpm dev --host --port 5174 >"$FRONTEND_LOG" 2>&1 &
    echo $! >"$FRONTEND_PID_FILE"
  )
  echo "frontend started, pid=$(cat "$FRONTEND_PID_FILE"), log=$FRONTEND_LOG"
fi

echo "mall demo started"
echo "frontend: http://localhost:5174"
echo "backend:  http://localhost:18080"
