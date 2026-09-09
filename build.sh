#!/bin/bash
set -e

STARSECTOR_DIR="../../"
MOD_DIR=$(pwd)

echo "Compiling Java sources..."
rm -rf bin && mkdir -p bin
mkdir -p jars

javac -encoding UTF-8 --release 17 -sourcepath src -d bin -cp "$STARSECTOR_DIR/*:$STARSECTOR_DIR/starfarer.api.jar:$STARSECTOR_DIR/mods/LazyLib/jars/*:$STARSECTOR_DIR/mods/GraphicsLib/jars/*:$STARSECTOR_DIR/mods/MagicLib/jars/*:$STARSECTOR_DIR/mods/Nexerelin/jars/*:$STARSECTOR_DIR/mods/LunaLib/jars/*" $(find src/ -name "*.java")

echo "Packaging JAR..."
jar cf jars/orbiting_spoons.jar -C bin data

echo "Build complete! Output: jars/orbiting_spoons.jar"
