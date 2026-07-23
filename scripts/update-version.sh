#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRONTEND_PKG="${PROJECT_ROOT}/frontend/package.json"
BACKEND_POM="${PROJECT_ROOT}/backend/pom.xml"

increment_version() {
  local version="$1"
  IFS='.' read -r x y z <<< "$version"

  z=$((z + 1))
  if [ "$z" -gt 999 ]; then
    z=0
    y=$((y + 1))
  fi
  if [ "$y" -gt 999 ]; then
    y=0
    x=$((x + 1))
  fi
  if [ "$x" -gt 999 ]; then
    echo "Error: version overflow (exceeds 999.999.999)" >&2
    exit 1
  fi

  echo "${x}.${y}.${z}"
}

if [ $# -eq 0 ]; then
  current_version=$(grep -o '"version": *"[^"]*"' "$FRONTEND_PKG" | sed 's/"version": *"//;s/"//')
  new_version=$(increment_version "$current_version")
  echo "Auto increment: ${current_version} -> ${new_version}"
elif [ $# -eq 1 ]; then
  new_version="$1"
  if ! echo "$new_version" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "Error: version must be in X.Y.Z format (e.g. 1.2.3)" >&2
    exit 1
  fi
  echo "Set version: ${new_version}"
else
  echo "Usage: $0 [version]" >&2
  echo "  No args    - auto increment patch version" >&2
  echo "  <version>  - set specific version (X.Y.Z)" >&2
  exit 1
fi

# Update frontend package.json
if [ -f "$FRONTEND_PKG" ]; then
  sed -i '' -E "s/\"version\": *\"[^\"]+\"/\"version\": \"${new_version}\"/" "$FRONTEND_PKG"
  echo "  Updated frontend/package.json"
else
  echo "  Warning: frontend/package.json not found"
fi

# Update backend pom.xml (target project version after <artifactId>backend</artifactId>)
if [ -f "$BACKEND_POM" ]; then
  sed -i '' -E '/<artifactId>backend<\/artifactId>/,/<\/version>/s|<version>[^<]+</version>|<version>'"${new_version}"'</version>|' "$BACKEND_POM"
  echo "  Updated backend/pom.xml"
else
  echo "  Warning: backend/pom.xml not found"
fi

echo "Done."
