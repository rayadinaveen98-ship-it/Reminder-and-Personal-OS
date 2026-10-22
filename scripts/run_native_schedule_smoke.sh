#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM
kotlinc \
  "$ROOT/spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/ReminderCore.kt" \
  "$ROOT/spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/Recurrence.kt" \
  "$ROOT/spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/SchedulerReconciler.kt" \
  "$ROOT/shared/src/commonMain/kotlin/dev/reminderos/core/NativeSchedulePort.kt" \
  "$ROOT/spikes/core-model/src/jvmSmoke/kotlin/dev/reminderos/core/NativeScheduleSmoke.kt" \
  -include-runtime -d "$WORK/native-schedule-smoke.jar"
java -jar "$WORK/native-schedule-smoke.jar"
