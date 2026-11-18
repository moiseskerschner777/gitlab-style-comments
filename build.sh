#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

./gradlew clean buildPlugin "$@"

echo
echo "Plugin built:"
for zip in "$PWD"/build/distributions/*.zip; do
    echo "$zip"
done
