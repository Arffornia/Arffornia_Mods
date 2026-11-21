#!/usr/bin/env bash
# usage: RES=WxH start.sh <log label>; starts the client, creates or opens the bench world, waits until in world
B="$(dirname "$(readlink -f "$0")")"; L="$B/client/logs/latest.log"; rm -f "$L"
(cd "$B" && setsid nix shell nixpkgs#portablemc -c ./run.sh > "$B/run-$1.log" 2>&1 &)
until grep -qE 'seconds to start|Preparing crash' "$L" 2>/dev/null; do sleep 3; done
sleep 10; "$B/cmd.sh" world
until grep -qE 'joined the game|Preparing crash' "$L" 2>/dev/null; do sleep 3; done
sleep "${SETTLE:-30}"; grep -cE 'Preparing crash' "$L" || true
