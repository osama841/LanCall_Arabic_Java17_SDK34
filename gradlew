#!/usr/bin/env sh
# Simplified wrapper launcher delegating to local gradle if present
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$DIR/gradlew" ]; then
  exec "$DIR/gradlew" "$@"
fi
# Fallback to system gradle
exec gradle "$@"
