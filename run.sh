#!/bin/sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$SCRIPT_DIR/.jdk/Contents/Home"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "JDK not found at $JAVA_HOME"
  echo "Download Temurin JDK 17 for macOS (arm64) and extract to .jdk/, or set JAVA_HOME yourself."
  exit 1
fi

cd "$SCRIPT_DIR"
exec ./mvnw "$@"
