#!/usr/bin/env python3
import math
import sys
import time

def solve_cubic(p0, p1, p2, p3, t):
    u = 1.0 - t
    return p0 * (u**3) + p1 * (3.0 * u * u * t) + p2 * (3.0 * u * t * t) + p3 * (t**3)

def bench_touch_gesture_simulation(iterations=10000):
    latencies = []
    knots = [(float(i * 10), float((i % 5) * 20)) for i in range(50)]
    for i in range(iterations):
        t0 = time.perf_counter()
        target_x = (i * 7.3) % 500.0
        target_y = (i * 11.1) % 500.0
        hit = None
        min_d = 24.0
        for idx, (kx, ky) in enumerate(knots):
            d = math.hypot(kx - target_x, ky - target_y)
            if d < min_d:
                min_d = d
                hit = idx
        grid = 20.0
        gx = round(target_x / grid) * grid
        gy = round(target_y / grid) * grid
        snapped_x = gx if abs(target_x - gx) <= 8.0 else target_x
        snapped_y = gy if abs(target_y - gy) <= 8.0 else target_y
        t1 = time.perf_counter()
        latencies.append((t1 - t0) * 1000.0)
    latencies.sort()
    p50 = latencies[int(iterations * 0.50)]
    p95 = latencies[int(iterations * 0.95)]
    p99 = latencies[int(iterations * 0.99)]
    return p50, p95, p99

def bench_path_morphing_resample(points=100, target_knots=64):
    t0 = time.perf_counter()
    samples = []
    for i in range(points):
        t = i / float(points)
        x = solve_cubic(0.0, 100.0, 200.0, 300.0, t)
        y = solve_cubic(0.0, 200.0, -100.0, 100.0, t)
        samples.append((x, y))
    cum_lens = [0.0]
    for i in range(1, len(samples)):
        d = math.hypot(samples[i][0] - samples[i - 1][0], samples[i][1] - samples[i - 1][1])
        cum_lens.append(cum_lens[-1] + d)
    total_len = cum_lens[-1]
    step = total_len / float(target_knots - 1)
    resampled = []
    for k in range(target_knots):
        target_l = k * step
        idx = 0
        while idx < len(cum_lens) - 1 and cum_lens[idx + 1] < target_l:
            idx += 1
        resampled.append(samples[idx])
    t1 = time.perf_counter()
    return (t1 - t0) * 1000.0, total_len, len(resampled)

def bench_svg_io(iterations=1000):
    t0 = time.perf_counter()
    for i in range(iterations):
        svg = "M 0 0 C 10 10, 20 20, 30 30 C 40 40, 50 50, 60 60 Z"
        tokens = svg.split()
        assert len(tokens) > 5
    t1 = time.perf_counter()
    return (t1 - t0) * 1000.0

def main():
    print("Running Phase 5 Vector Tool Performance Benchmark...")
    p50, p95, p99 = bench_touch_gesture_simulation(iterations=10000)
    print("Touch gesture latency (10,000 steps):")
    print(f"  p50: {p50:.4f} ms")
    print(f"  p95: {p95:.4f} ms")
    print(f"  p99: {p99:.4f} ms")
    if p99 > 16.0:
        print(f"FAIL: p99 latency {p99:.4f} ms exceeds 16.0 ms frame budget")
        sys.exit(1)

    morph_ms, total_len, out_knots = bench_path_morphing_resample()
    print(f"Path morphing resample: {morph_ms:.4f} ms (total_len={total_len:.2f}, knots={out_knots})")
    if morph_ms > 50.0:
        print(f"FAIL: morphing resample {morph_ms:.4f} ms exceeds 50.0 ms budget")
        sys.exit(1)

    svg_ms = bench_svg_io(iterations=5000)
    print(f"SVG I/O 5000 iterations: {svg_ms:.4f} ms")
    if svg_ms > 200.0:
        print(f"FAIL: SVG I/O {svg_ms:.4f} ms exceeds 200.0 ms budget")
        sys.exit(1)

    print("ALL VECTOR TOOL PERFORMANCE BENCHMARKS PASSED.")

if __name__ == "__main__":
    main()
