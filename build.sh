#!/bin/bash
set -e

cd "$(dirname "$0")"

STARSECTOR_CORE="../.."
MODS_DIR=".."

CLASSPATH=""
for jar in "$STARSECTOR_CORE"/*.jar; do
    [ -f "$jar" ] && CLASSPATH="${CLASSPATH:+$CLASSPATH:}$jar"
done
for jar in \
    "$MODS_DIR"/LazyLib*/jars/*.jar \
    "$MODS_DIR"/LazyLib*/jars/internal/*.jar \
    "$MODS_DIR"/MagicLib*/jars/*.jar \
    "$MODS_DIR"/*GraphicsLib*/jars/*.jar \
    "$MODS_DIR"/Nexerelin*/jars/*.jar \
    "$MODS_DIR"/LunaLib*/jars/*.jar; do
    [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done

rm -rf bin sources.txt
mkdir -p bin jars
find src -name "*.java" | sort > sources.txt

javac -encoding UTF-8 -Xlint:-options --release 17 -sourcepath "" -implicit:none -cp "$CLASSPATH" -d bin @sources.txt
jar cf jars/orbiting_spoons.jar -C bin .
rm -rf bin sources.txt

echo "Build complete! Output: jars/orbiting_spoons.jar"
