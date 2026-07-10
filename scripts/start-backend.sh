#!/usr/bin/env bash
# Sobe PostgreSQL + backend Spring Boot.
# Uso: ./scripts/start-backend.sh [--reset-db]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/dev-env.sh
source "$ROOT/scripts/dev-env.sh"

RESET_DB=false
if [[ "${1:-}" == "--reset-db" ]]; then
  RESET_DB=true
fi

cd "$ROOT"

if ! docker info >/dev/null 2>&1; then
  echo "ERRO: Docker Desktop não está rodando. Abra o Docker Desktop e tente de novo." >&2
  exit 1
fi

if $RESET_DB; then
  echo ">> Resetando banco (docker compose down -v)..."
  docker compose down -v
fi

echo ">> Subindo PostgreSQL..."
docker compose up -d

echo ">> Aguardando PostgreSQL..."
for i in {1..30}; do
  if docker compose exec -T postgres pg_isready -U guild -d guild_points >/dev/null 2>&1; then
    echo ">> PostgreSQL pronto."
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERRO: PostgreSQL não respondeu a tempo." >&2
    exit 1
  fi
  sleep 1
done

cd "$ROOT/backend"
chmod +x mvnw 2>/dev/null || true

echo ">> Iniciando backend (porta 8080)..."
exec ./mvnw spring-boot:run
