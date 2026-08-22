#!/bin/sh
set -e

UPLOAD_DIR="${UPLOAD_PATH:-/data/uploads}"
mkdir -p "$UPLOAD_DIR"
chown -R guild:guild "$UPLOAD_DIR" 2>/dev/null || true

exec su-exec guild sh -c "exec java $JAVA_OPTS -jar /app/app.jar"
