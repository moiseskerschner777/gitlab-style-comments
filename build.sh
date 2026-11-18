#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

./gradlew clean buildPlugin "$@"

echo
echo "Plugin built:"
ls -1 build/distributions/*.zip
