#!/bin/sh
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle не найден в Android IDE" >&2
exit 1
