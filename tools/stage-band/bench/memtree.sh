#!/usr/bin/env bash
# usage: memtree.sh <label> [seconds]; PSS/RSS of the game java process and its descendants, CPU over the window
root=""
for p in $(pgrep -x .java-wrapped); do case "$(readlink /proc/$p/cwd)" in */d2bench/client*) root=$p;; esac; done
[ -z "$root" ] && { echo "no client"; exit 1; }
desc() { echo "$1"; for c in $(cat /proc/$1/task/*/children 2>/dev/null); do desc "$c"; done; }
pids=$(desc "$root")
ticks() { for p in $@; do awk '{print $14+$15}' /proc/$p/stat 2>/dev/null; done | awk '{s+=$1} END {print s+0}'; }
kids=$(echo "$pids" | grep -vx "$root")
t0=$(ticks $kids); j0=$(ticks $root); sleep "${2:-10}"; t1=$(ticks $kids); j1=$(ticks $root)
hz=$(getconf CLK_TCK); s=${2:-10}
cpu_kids=$(awk -v a=$t0 -v b=$t1 -v h=$hz -v s=$s 'BEGIN{printf "%.1f",(b-a)*100/h/s}'); cpu_java=$(awk -v a=$j0 -v b=$j1 -v h=$hz -v s=$s 'BEGIN{printf "%.1f",(b-a)*100/h/s}')
jp=$(awk '/^Pss:/{print $2}' /proc/$root/smaps_rollup); jr=$(awk '/^Rss:/{print $2}' /proc/$root/smaps_rollup)
hp=0; hr=0; n=0
for p in $kids; do n=$((n+1)); hp=$((hp + $(awk '/^Pss:/{print $2}' /proc/$p/smaps_rollup 2>/dev/null || echo 0))); hr=$((hr + $(awk '/^Rss:/{print $2}' /proc/$p/smaps_rollup 2>/dev/null || echo 0))); done
echo "{\"label\":\"$1\",\"java_pss_mb\":$((jp/1024)),\"java_rss_mb\":$((jr/1024)),\"helpers\":$n,\"helpers_pss_mb\":$((hp/1024)),\"helpers_rss_mb\":$((hr/1024)),\"total_pss_mb\":$(((jp+hp)/1024)),\"helpers_cpu_pct\":$cpu_kids,\"java_cpu_pct\":$cpu_java}"
