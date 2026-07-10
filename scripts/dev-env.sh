#!/usr/bin/env bash
# Configura JDK 21 para o projeto (Git Bash / Windows).
# Uso: source scripts/dev-env.sh

set -euo pipefail

_find_jdk21() {
  local dir
  for dir in \
    "/c/Program Files/Eclipse Adoptium"/jdk-21.* \
    "/c/Program Files/Java"/jdk-21.* \
    "/c/Program Files/Microsoft"/jdk-21.*; do
    if [ -d "$dir" ] && [ -x "$dir/bin/java" ]; then
      echo "$dir"
      return 0
    fi
  done
  return 1
}

if ! JAVA_HOME="$(_find_jdk21)"; then
  echo "ERRO: JDK 21 não encontrado." >&2
  echo "Instale: winget install EclipseAdoptium.Temurin.21.JDK" >&2
  return 1 2>/dev/null || exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# Raiz do monorepo (para scripts que fazem source)
export GUILD_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "JAVA_HOME=$JAVA_HOME"
java -version
