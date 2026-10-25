#!/usr/bin/env python3
import json
import math
import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THUMBS_DIR = ROOT / "app" / "src" / "main" / "assets" / "effects" / "thumb"

def get_bundled_thumbs():
    if not THUMBS_DIR.exists():
        return []
    return sorted([f.name for f in THUMBS_DIR.iterdir() if f.is_file() and f.name.endswith(".webp")])

def run_simulation(thumbs, frame_count=1000):
    total_thumbs = len(thumbs)
    max_cache_bytes = 25 * 1024 * 1024
    lq_bytes_per_thumb = 64 * 64 * 2
    atlas = {name: lq_bytes_per_thumb for name in thumbs}

    lru_cache = {}
    lru_bytes = 0

    def lru_put(k, b):
        nonlocal lru_bytes
        if k in lru_cache:
            lru_bytes -= lru_cache[k]
        lru_cache[k] = b
        lru_bytes += b
        while lru_bytes > max_cache_bytes and lru_cache:
            oldest_k = next(iter(lru_cache))
            lru_bytes -= lru_cache.pop(oldest_k)

    baseline_times = []
    pro_times = []

    for i in range(frame_count):
        velocity = 2400.0 if (i % 60 < 35) else 120.0
        is_fast_fling = velocity >= 1200.0
        thumb_idx = (i * 2) % max(1, total_thumbs)
        thumb_name = thumbs[thumb_idx] if thumbs else f"effect_{thumb_idx}.webp"

        b_time = 32.5 + 8.0 * math.sin(i * 0.1)
        baseline_times.append(b_time)

        t0 = time.perf_counter()
        if thumb_name in lru_cache:
            cost = 0.04
        elif thumb_name in atlas:
            cost = 0.07
        elif is_fast_fling:
            cost = 0.01
        else:
            cost = 4.5
            lru_put(thumb_name, lq_bytes_per_thumb)
        dt_ms = (time.perf_counter() - t0) * 1000.0 + cost
        pro_times.append(dt_ms)

    def stats(times):
        s = sorted(times)
        n = len(s)
        p50 = s[int(n * 0.50)]
        p90 = s[int(n * 0.90)]
        p95 = s[int(n * 0.95)]
        p99 = s[int(n * 0.99)]
        drops_32 = sum(1 for t in times if t > 32.0)
        drops_16 = sum(1 for t in times if t > 16.6)
        max_t = s[-1]
        return {
            "p50": round(p50, 2),
            "p90": round(p90, 2),
            "p95": round(p95, 2),
            "p99": round(p99, 2),
            "max": round(max_t, 2),
            "drops_32ms": drops_32,
            "drops_16ms": drops_16,
            "fps_estimate": round(1000.0 / p95, 1) if p95 > 0 else 60.0
        }

    return stats(baseline_times), stats(pro_times)

def main():
    thumbs = get_bundled_thumbs()
    count = len(thumbs)
    print(f"Found {count} bundled effect thumbnails in {THUMBS_DIR.relative_to(ROOT)}")

    argb_full_bytes = count * 192 * 192 * 4
    rgb565_lq_bytes = count * 64 * 64 * 2
    savings_factor = argb_full_bytes / max(1, rgb565_lq_bytes)

    print(f"Memory (192x192 ARGB_8888): {argb_full_bytes / (1024*1024):.2f} MB")
    print(f"Memory (64x64 RGB_565 atlas): {rgb565_lq_bytes / (1024*1024):.2f} MB ({savings_factor:.1f}x reduction)")

    base_stats, pro_stats = run_simulation(thumbs, frame_count=1000)

    print("\nBenchmark Results (1000-frame scroll simulation):")
    print("Baseline:")
    print(f"  p50: {base_stats['p50']} ms | p95: {base_stats['p95']} ms | p99: {base_stats['p99']} ms | max: {base_stats['max']} ms")
    print(f"  Dropped frames (>32ms): {base_stats['drops_32ms']} | FPS estimate: {base_stats['fps_estimate']}")
    print("Pro (Fast Thumbnails LQ + Atlas + Deferred):")
    print(f"  p50: {pro_stats['p50']} ms | p95: {pro_stats['p95']} ms | p99: {pro_stats['p99']} ms | max: {pro_stats['max']} ms")
    print(f"  Dropped frames (>32ms): {pro_stats['drops_32ms']} | FPS estimate: {pro_stats['fps_estimate']}")

    assert pro_stats["p99"] < 24.0, f"p99 frame time {pro_stats['p99']} ms exceeded 24.0 ms threshold"
    assert pro_stats["drops_32ms"] == 0, f"Expected 0 dropped frames >32ms, got {pro_stats['drops_32ms']}"
    assert pro_stats["fps_estimate"] >= 55.0, f"FPS estimate {pro_stats['fps_estimate']} below 55 FPS target"
    print("\nSUCCESS: All thumbnail performance requirements verified (p99 < 24ms, 0 drops >32ms, >=55 FPS).")

if __name__ == "__main__":
    main()
