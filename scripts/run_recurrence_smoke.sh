#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT HUP INT TERM
kotlinc \
  "$ROOT/spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/Recurrence.kt" \
  "$ROOT/spikes/core-model/src/commonMain/kotlin/dev/reminderos/core/SchedulerReconciler.kt" \
  "$ROOT/spikes/core-model/src/jvmSmoke/kotlin/dev/reminderos/core/RecurrenceSmoke.kt" \
  -include-runtime -d "$WORK/recurrence-smoke.jar"
java -jar "$WORK/recurrence-smoke.jar"
