#!/usr/bin/env bash
# usage: RES=1280x720 run.sh ; full pack client on the real display (Xwayland, NVIDIA)
set -u
cd "$(dirname "$0")"
export WMC_RUNTIME_DIR="$PWD/wmc-runtime"
export LD_LIBRARY_PATH="/run/opengl-driver/lib:$PWD/cef-libs/lib:$PWD/extra-libs/lib:${JDK_LIB:?set JDK_LIB to the JDK lib dir holding libjawt.so}"
export DISPLAY=${DISPLAY:-:0}
exec portablemc --main-dir "$PWD/mc-home" start neoforge::21.1.172 \
  --mc-dir "$PWD/client" --jvm /run/current-system/sw/bin/java \
  --jvm-arg=-Xmx10G --resolution "${RES:-1280x720}" -u BandBench ${JOIN:+--join-world $JOIN}
