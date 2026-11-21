#!/usr/bin/env bash
D="$(dirname "$(readlink -f "$0")")/client/arffornia-band"; mkdir -p "$D"; echo "$1" > "$D/cmd"
until [ ! -f "$D/cmd" ]; do sleep 0.5; done
