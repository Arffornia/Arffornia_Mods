#!/usr/bin/env bash
# usage: follow.sh <prefix>; samples memtree for 10 s inside every bench phase until the bench writes its results
B="$(dirname "$(readlink -f "$0")")"; F="$B/client/arffornia-band/phase"; last=""
while :; do
  ph=$(cat "$F" 2>/dev/null)
  if [ -n "$ph" ] && [ "$ph" != "$last" ]; then last=$ph; sleep 2; "$B/memtree.sh" "$1-$ph" 10 >> "$B/mem.jsonl"; fi
  [ -z "$ph" ] && last=""
  ls "$B"/client/arffornia-band/bench-*.json >/dev/null 2>&1 && break
  sleep 0.5
done
