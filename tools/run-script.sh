#!/bin/sh
# Runs an EMI dev script in the dev client and waits for it to quit.
# Usage: tools/run-script.sh tests/<name>.emis [extra gradle args]   (log: run-log.txt, shots: forge-1.16.5/run/screenshots)
cd "$(dirname "$0")/.." || exit 1
S=$(realpath "$1"); shift
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'EMILegacy' -and \$_.CommandLine -match 'devlaunchinjector|LaunchTesting' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
cd forge-1.16.5 && JAVA_HOME="$HOME/.jdks/jdk-17.0.21+9" timeout 900 ./gradlew runClient -PdevScript="$S" "$@" --no-daemon > ../run-log.txt 2>&1
echo "exit=$?"
cd .. && grep -aE "\[devscript\]|Reloaded EMI|FATAL|Critical|Exception|---- Minecraft Crash|BUILD FAILED" run-log.txt | grep -av "Realms\|Unable to load model\|appender" | cut -c1-260
