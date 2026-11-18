"""How long each wave takes: from the mobs spawning to the claim, in seconds.

    python pace.py ckpt --kind full --episodes 6 [--teacher]
"""
import argparse
import json
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from chestai.env import VecEnv  # noqa: E402
from chestai.model import load_policy  # noqa: E402
from evaluate import SUITE  # noqa: E402
from train import stage_params  # noqa: E402


def run(policy, kind, seed, teacher, extra):
    st, ex = SUITE[kind]
    env = VecEnv(1, seed=seed)
    p = stage_params(st, ex)
    p.update(extra)
    for k, v in p.items():
        env.set_param(k, v)
    obs, priv = env.reset()
    state = policy.initial_state(1) if policy is not None else None
    prev = None
    start, cleared = {}, {}
    out = dict(clear=[], claim=[], reset=0, claims=0, ticks=0, busy=0)
    for t in range(20000):
        if teacher:
            act = env.teacher()
        else:
            a, _, _, state = policy.act(torch.from_numpy(obs).clone(), torch.from_numpy(priv).clone(), state)
            act = a.numpy().astype(np.int32)
        f = json.loads(env.json(0, 1))
        cs = [c[3] for c in f["chests"]]
        if any(s in (2, 3) for s in cs):
            out["busy"] += 1
        if prev is not None:
            for i, (a0, a1) in enumerate(zip(prev, cs)):
                if a0 != 2 and a1 == 2:
                    start[i] = t
                if a0 == 2 and a1 == 3 and i in start:
                    out["clear"].append((t - start[i]) / 20)
                    cleared[i] = t
                if a0 == 2 and a1 in (0, 1):
                    out["reset"] += 1
                if a0 == 3 and a1 == 0:
                    out["claims"] += 1
                    if i in start:
                        out["claim"].append((t - start[i]) / 20)
        prev = cs
        obs, priv, rew, done, _, _ = env.step(act)
        out["ticks"] = t + 1
        if done[0]:
            break
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ckpt", nargs="?", default="")
    ap.add_argument("--kind", default="full")
    ap.add_argument("--episodes", type=int, default=6)
    ap.add_argument("--teacher", action="store_true")
    ap.add_argument("--param", action="append", default=[])
    args = ap.parse_args()
    torch.set_num_threads(1)
    extra = {k: float(v) for k, v in (s.split("=") for s in args.param)}
    pol = None
    if not args.teacher:
        pol, _ = load_policy(args.ckpt)
        pol.eval()
    tot = dict(clear=[], claim=[], reset=0, claims=0, ticks=0, busy=0)
    for j in range(args.episodes):
        o = run(pol, args.kind, 900000 + 17 * j, args.teacher, extra)
        for k in tot:
            tot[k] += o[k]
    c, cl = np.array(tot["clear"]), np.array(tot["claim"])
    print(json.dumps({
        "waves_cleared": len(c), "resets": tot["reset"], "claims": tot["claims"],
        "clear_s_median": round(float(np.median(c)), 1) if len(c) else None,
        "spawn_to_claim_s_median": round(float(np.median(cl)), 1) if len(cl) else None,
        "claims_per_min": round(tot["claims"] / (tot["ticks"] / 1200), 2),
        "busy_share": round(tot["busy"] / tot["ticks"], 2),
    }))


if __name__ == "__main__":
    main()
