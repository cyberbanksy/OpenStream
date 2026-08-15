import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import test from "node:test";

const scriptPath = join(dirname(fileURLToPath(import.meta.url)), "android-device-smoke.sh");

const mockAdb = String.raw`#!/bin/sh
set -eu

state_file=\${MOCK_ADB_STATE:?}
events_file=\${MOCK_ADB_EVENTS:?}
command_name=\${1:-}
shift || true

if [ "$command_name" = "devices" ]; then
  printf 'List of devices attached\nMOCK123\tdevice\n'
elif [ "$command_name" = "get-state" ]; then
  printf 'device\n'
elif [ "$command_name" = "exec-out" ]; then
  state=$(cat "$state_file")
  case "$state" in
    launch:*)
      remaining=\${state#launch:}
      if [ "$remaining" -gt 0 ]; then
        printf 'launch:%s\n' "$((remaining - 1))" > "$state_file"
        printf '<hierarchy><node package="com.orgista.openstream"><node text="OpenStream" bounds="[0,0][50,50]" /></node></hierarchy>\n'
        exit 0
      fi
      printf 'sources\n' > "$state_file"
      state=sources
      ;;
  esac
  body='<node text="Sources" bounds="[0,100][200,200]" />'
  if [ "$state" = "discover" ]; then
    body='<node text="Play" bounds="[0,100][100,200]" /><node text="Details" bounds="[100,100][200,200]" />'
  elif [ "$state" = "library" ]; then
    body='<node text="Library" bounds="[0,100][200,200]" />'
  fi
  printf '<hierarchy><node package="com.orgista.openstream"><node text="OpenStream" bounds="[0,0][50,50]" /><node text="Discover" bounds="[0,0][100,100]" /><node text="Library" bounds="[100,0][200,100]" /><node content-desc="Search" bounds="[200,0][250,100]" /><node content-desc="More options" bounds="[250,0][300,100]" />%s</node></hierarchy>\n' "$body"
elif [ "$command_name" = "logcat" ]; then
  if [ "\${MOCK_ADB_CRASH:-0}" = "1" ]; then
    printf 'FATAL EXCEPTION: main\nProcess: com.orgista.openstream\n'
  fi
elif [ "$command_name" = "shell" ]; then
  shell_command=$*
  case "$shell_command" in
    "dumpsys package "*)
      printf '  versionCode=1 minSdk=26 targetSdk=37\n  versionName=0.1.0\n  User 0: installed=true enabled=0\n'
      ;;
    "dumpsys power")
      printf 'mWakefulness=%s\n' "\${MOCK_ADB_POWER:-Awake}"
      ;;
    "input keyevent KEYCODE_WAKEUP")
      printf 'wake\n' >> "$events_file"
      ;;
    "input keyevent KEYCODE_SLEEP")
      printf 'sleep\n' >> "$events_file"
      ;;
    "am start -W "*)
      printf 'Status: ok\nActivity: com.orgista.openstream/.NativeAppActivity\n'
      ;;
    "uiautomator dump "*)
      printf 'UI hierarchy dumped\n'
      ;;
    "pidof "*)
      printf '4242\n'
      ;;
    "input tap "*)
      set -- $shell_command
      x=$3
      if [ "$x" -lt 100 ]; then
        printf 'discover\n' > "$state_file"
      else
        printf 'library\n' > "$state_file"
      fi
      ;;
    "getprop ro.product.model")
      printf 'Mock Tablet\n'
      ;;
    "dumpsys activity activities")
      printf '  mLockTaskModeState=LOCKED\n'
      ;;
    "rm -f "*)
      ;;
    *)
      printf 'Unexpected mock adb shell command: %s\n' "$shell_command" >&2
      exit 2
      ;;
  esac
else
  printf 'Unexpected mock adb command: %s %s\n' "$command_name" "$*" >&2
  exit 2
fi
`.replaceAll("\\${", "${");

async function runSmoke(overrides = {}) {
  const directory = await mkdtemp(join(tmpdir(), "openstream-device-smoke-test-"));
  const adbPath = join(directory, "adb");
  const statePath = join(directory, "state");
  const eventsPath = join(directory, "events");
  await Promise.all([
    writeFile(adbPath, mockAdb, { mode: 0o700 }),
    writeFile(statePath, overrides.MOCK_ADB_INITIAL_STATE || "sources\n"),
    writeFile(eventsPath, ""),
  ]);
  const { ANDROID_SERIAL: _ignored, ...baseEnvironment } = process.env;
  const result = spawnSync("sh", [scriptPath], {
    encoding: "utf8",
    env: {
      ...baseEnvironment,
      ADB: adbPath,
      MOCK_ADB_STATE: statePath,
      MOCK_ADB_EVENTS: eventsPath,
      TMPDIR: directory,
      ...overrides,
    },
  });
  const events = await readFile(eventsPath, "utf8");
  await rm(directory, { recursive: true, force: true });
  return { ...result, events };
}

test("attached-device smoke validates launch, navigation, and sleep restoration", async () => {
  const result = await runSmoke({ MOCK_ADB_POWER: "Dozing" });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Android device smoke passed: model=Mock Tablet/);
  assert.match(result.stdout, /navigation=1 lockTask=LOCKED/);
  assert.equal(result.events, "wake\nsleep\n");
});

test("attached-device smoke waits for the native launch sequence", async () => {
  const result = await runSmoke({ MOCK_ADB_INITIAL_STATE: "launch:2\n" });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Android device smoke passed/);
});

test("attached-device smoke fails closed on a process crash", async () => {
  const result = await runSmoke({ MOCK_ADB_CRASH: "1" });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /recent process logs contain a crash or ANR/);
});
