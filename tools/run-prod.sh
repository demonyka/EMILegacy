#!/bin/sh
# Production test: builds the release jar, runs it in a real Forge 1.16.5 client through HeadlessMC
# with the mods in $HMC/game/mods, executing an EMI dev script. Usage: tools/run-prod.sh tests/<name>.emis
# HMC must point to a HeadlessMC dir with Forge 1.16.5-36.2.39 installed and a captured launch
# command line (cmdline-template.txt, see tools/launch-prod.js).
cd "$(dirname "$0")/.." || exit 1
HMC=${HMC:?set HMC to the HeadlessMC directory}
S=$(cygpath -m "$(realpath "$1")")
(cd forge-1.16.5 && JAVA_HOME="$HOME/.jdks/jdk-17.0.21+9" ./gradlew build --no-daemon -q) || exit 1
V=$(grep "^mod_version=" forge-1.16.5/gradle.properties | cut -d= -f2)
rm -f "${HMC:?}"/game/mods/emi-*.jar
cp "forge-1.16.5/build/libs/emi-$V+1.16.5+forge.jar" "$HMC/game/mods/"
rm -rf "${HMC:?}/game/saves/emi-test" "${HMC:?}/game/screenshots" "${HMC:?}/game/crash-reports"
HMC="$HMC" node tools/launch-prod.js "$S"
echo "exit=$?"
grep -aE "EMI/\]: \[EMI\]|FATAL|Game crashed|Mixin apply failed" "$HMC/game/logs/latest.log" | sed -E 's/^\[[^]]*\] //' | grep -av "\[devscript\] (wait|cmd|key|move|type)" | cut -c1-230
ls "$HMC/game/crash-reports" 2>/dev/null
