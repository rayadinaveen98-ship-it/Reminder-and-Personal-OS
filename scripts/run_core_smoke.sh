#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM
kotlinc \
  "$ROOT/spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/ReminderCore.kt" \
  "$ROOT/spikes/core-model/src/jvmSmoke/kotlin/dev/reminderos/core/ReminderCoreSmoke.kt" \
  -include-runtime -d "$WORK/reminder-core-smoke.jar"
java -jar "$WORK/reminder-core-smoke.jar"
