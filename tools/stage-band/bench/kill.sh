#!/usr/bin/env bash
for p in $(pgrep -x .java-wrapped); do case "$(readlink /proc/$p/cwd)" in */d2bench/client*) kill $p;; esac; done
sleep 5
