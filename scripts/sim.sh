#!/bin/bash
# Proteus Simulator Runner
# Memory limit: 512MB (realistic VPS constraint)

JAR="target/proteus-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building..."
    mvn clean package -q -DskipTests
fi

# -Xmx512m: VPS-realistic memory limit
# -XX:+UseG1GC: better for long-running simulations
# -XX:+ExitOnOutOfMemoryError: fail fast, don't hang
java -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError \
    -jar "$JAR" run "$@"
