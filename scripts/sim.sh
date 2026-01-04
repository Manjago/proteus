#!/bin/bash
# Proteus Simulator Runner
#
# Performance notes:
#   - 100K soup: ~130 cycles/sec, constant defragmentation, memory-bound
#   - 1M soup:   ~190 cycles/sec, no defragmentation, 46% faster!
#
# Recommended: Use 1M soup unless testing memory pressure scenarios
#
# Usage:
#   ./sim.sh                           # Run with ancestor
#   ./sim.sh --inject myorg.asm        # Run with custom organism
#   SOUP_SIZE=100000 ./sim.sh          # Override soup size

JAR="target/proteus-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Building..."
    mvn clean package -q -DskipTests
fi

# Default parameters (can override via command line)
SOUP_SIZE="${SOUP_SIZE:-1000000}"      # 1M cells (was 100K)
MAX_CYCLES="${MAX_CYCLES:-250000}"      # 250K cycles (~20 min at 190 c/s)
MAX_ORGANISMS="${MAX_ORGANISMS:-5000}"

# Default organism if not specified
INJECT_FILE="${INJECT_FILE:-examples/ancestor.asm}"

# -Xmx512m: VPS-realistic memory limit
# -XX:+UseG1GC: better for long-running simulations
java -Xmx512m -XX:+UseG1GC \
    -jar "$JAR" run \
    --inject "$INJECT_FILE" \
    -s "$SOUP_SIZE" \
    -c "$MAX_CYCLES" \
    --max-organisms "$MAX_ORGANISMS" \
    "$@"
