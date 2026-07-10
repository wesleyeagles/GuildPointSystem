#!/usr/bin/env bash
# Reset DB + sobe backend. Rode o frontend em outro terminal: ./scripts/start-frontend.sh
# Uso: ./scripts/dev.sh

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT/scripts/start-backend.sh" --reset-db
