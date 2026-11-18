#!/usr/bin/env bash
# Carry weights between the training machines through one git branch per
# machine: push our own file of this round to sync/m<ours>, then wait for
# the other machines' files of the same round and copy them into the share
# folder. Usage: sync.sh SHARE_DIR MACHINE MACHINES ROUND
set -u
S=$1; M=$2; N=$3; R=$4
f="$S/m${M}_r${R}.pt"
W=/tmp/syncrepo
if [ ! -d "$W/.git" ]; then
  git init -q "$W"
  git -C "$W" remote add origin "$(git -C "$GITHUB_WORKSPACE" remote get-url origin)"
  h=$(git -C "$GITHUB_WORKSPACE" config --get http.https://github.com/.extraheader || true)
  [ -n "$h" ] && git -C "$W" config http.https://github.com/.extraheader "$h"
  git -C "$W" config user.name "trainer"
  git -C "$W" config user.email "trainer@users.noreply.github.com"
fi
cd "$W"
git checkout -q --orphan "w$R" 2>/dev/null || git checkout -q "w$R"
git rm -rqf --cached . >/dev/null 2>&1 || true
rm -f ./*.pt
cp "$f" . && git add "m${M}_r${R}.pt" && git commit -qm "machine $M round $R"
for i in 1 2 3 4 5; do git push -qf origin "HEAD:refs/heads/sync/m$M" && break; sleep $((i * 3)); done
end=$((SECONDS + 200))
while [ $SECONDS -lt $end ]; do
  missing=0
  git fetch -q --depth 1 origin "+refs/heads/sync/*:refs/remotes/origin/sync/*" 2>/dev/null || true
  for j in $(seq 0 $((N - 1))); do
    [ "$j" = "$M" ] && continue
    out="$S/m${j}_r${R}.pt"
    [ -s "$out" ] && continue
    if git cat-file -e "origin/sync/m$j:m${j}_r${R}.pt" 2>/dev/null; then
      git show "origin/sync/m$j:m${j}_r${R}.pt" > "$out.tmp" && mv "$out.tmp" "$out"
    else
      missing=1
    fi
  done
  [ $missing = 0 ] && break
  sleep 10
done
ls "$S" | tail -n 20
