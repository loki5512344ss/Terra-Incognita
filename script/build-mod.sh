#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../custom_mods/incognita"
./gradlew build "$@"
ls -1 build/libs/*.jar
