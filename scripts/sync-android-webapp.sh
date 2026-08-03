#!/usr/bin/env bash
# Builds the web app and bundles it into the Android XR target's assets so
# WebAppActivity can serve the real OpenStream UI offline.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rm -rf "$ROOT/dist"
"$ROOT/node_modules/.bin/tsc" --noEmit
"$ROOT/node_modules/.bin/vite" build --configLoader runner
ASSETS="$ROOT/native/android-xr/app/src/main/assets"
rm -rf "$ASSETS"
mkdir -p "$ASSETS"
cp -R "$ROOT/dist/." "$ASSETS/"
echo "Web app bundled into $ASSETS"
