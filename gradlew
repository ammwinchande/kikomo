#!/bin/sh
# Kikomo Gradle wrapper: uses vendored wrapper jar when valid, else system gradle.
DIR=$(dirname "$0")
if java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" --version >/dev/null 2>&1; then
  exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
elif command -v gradle >/dev/null 2>&1; then
  echo "gradlew: wrapper jar unavailable, using system gradle" >&2
  exec gradle "$@"
else
  echo "gradlew: no Gradle found. Install Gradle 8+ or restore gradle-wrapper.jar via 'gradle wrapper'." >&2; exit 1
fi
