#!/bin/bash
# Installs Temurin JDK 25 and warms the Gradle/Loom caches (Minecraft 26.3, Fabric) for cloud sessions.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JDK_ROOT=/opt/jdk
JAVA_HOME_DIR=$(ls -d "$JDK_ROOT"/jdk-25* 2>/dev/null | head -1 || true)
if [ -z "$JAVA_HOME_DIR" ] || [ ! -x "$JAVA_HOME_DIR/bin/java" ]; then
  mkdir -p "$JDK_ROOT"
  curl -sSfL -o "$JDK_ROOT/jdk25.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar xzf "$JDK_ROOT/jdk25.tar.gz" -C "$JDK_ROOT"
  rm -f "$JDK_ROOT/jdk25.tar.gz"
  JAVA_HOME_DIR=$(ls -d "$JDK_ROOT"/jdk-25* | head -1)
fi

export JAVA_HOME="$JAVA_HOME_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=\"$JAVA_HOME\"" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\"$JAVA_HOME/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"
chmod +x gradlew
# Resolves Gradle, Loom, Minecraft 26.3 and Fabric dependencies and compiles main and test sources.
./gradlew --no-daemon --quiet testClasses
