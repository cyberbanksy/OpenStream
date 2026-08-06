#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_root=${TMPDIR:-/tmp}/openstream-openxr-core
mkdir -p "$build_root"

clang++ \
  -std=c++20 \
  -Wall \
  -Wextra \
  -Werror \
  -I"$project_root/native/openxr/shared" \
  "$project_root/native/openxr/shared/immersive_video_geometry.cpp" \
  "$project_root/native/openxr/tests/immersive_video_geometry_test.cpp" \
  -o "$build_root/immersive-video-geometry-test"

"$build_root/immersive-video-geometry-test"

clang++ \
  -std=c++20 \
  -Wall \
  -Wextra \
  -Werror \
  -I"$project_root/native/openxr/shared" \
  "$project_root/native/openxr/shared/immersive_video_geometry.cpp" \
  "$project_root/native/openxr/shared/immersive_video_session.cpp" \
  "$project_root/native/openxr/tests/immersive_video_session_test.cpp" \
  -o "$build_root/immersive-video-session-test"

"$build_root/immersive-video-session-test"
