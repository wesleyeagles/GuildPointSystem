#!/usr/bin/env bash
# Sobe frontend Vite.
# Uso: ./scripts/start-frontend.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/frontend"

if [ ! -d node_modules ]; then
  echo ">> Instalando dependências npm..."
  npm install
fi

echo ">> Frontend em http://localhost:5173"
exec npm run dev
