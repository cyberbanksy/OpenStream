#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
build_root=${TMPDIR:-/tmp}/openstream-quest-layer-test
mkdir -p "$build_root"

clang++ \
  -std=c++20 \
  -Wall \
  -Wextra \
  -Werror \
  -I"$project_root/native/openxr/shared" \
  -I"$project_root/native/meta-quest/app/src/main/cpp" \
  "$project_root/native/meta-quest/app/src/main/cpp/quest_video_layer_plan.cpp" \
  "$project_root/native/meta-quest/app/src/test/cpp/quest_video_layer_plan_test.cpp" \
  -o "$build_root/quest-video-layer-plan-test"

"$build_root/quest-video-layer-plan-test"
