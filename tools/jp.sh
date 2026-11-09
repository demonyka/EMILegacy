#!/bin/sh
# javap against Minecraft+Forge 1.16.5 (Yarn names) and JEI 7. Usage: tools/jp.sh [-p] class.Name ...
D=$(dirname "$0")/../forge-1.16.5/.gradle/loom-cache
MC=$(find "$D/minecraftMaven" -name "*.jar" ! -name "*sources*" | head -1)
JEI=$(find "$D/remapped_mods" -name "jei-1.16.5-*.jar" | head -1)
"$HOME/.jdks/jdk-17.0.21+9/bin/javap" -cp "$MC;$JEI" "$@" 2>&1 | grep -v "^Compiled from"
