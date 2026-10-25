#!/usr/bin/env python3
import math
import sys


def generate_scale_steps(min_scale=0.25, max_scale=8.0, steps=40):
    log_min = math.log(min_scale)
    log_max = math.log(max_scale)
    result = []
    for i in range(steps):
        t = i / float(steps - 1)
        val = math.exp(log_min + t * (log_max - log_min))
        result.append(round(val, 4))
    return result


def evaluate_scale_sweep(scale_steps, base_ink_area=1000.0, artificial_jump_at=None):
    results = []
    failed_steps = []
    prev_ratio = None

    for i, s in enumerate(scale_steps):
        simulated_jump = 1.15 if (artificial_jump_at is not None and i >= artificial_jump_at) else 1.0
        measured_ink = base_ink_area * (s ** 2) * simulated_jump
        normalized_ratio = measured_ink / (s ** 2)

        if prev_ratio is not None:
            delta_pct = abs(normalized_ratio - prev_ratio) / prev_ratio * 100.0
            if delta_pct > 5.0:
                failed_steps.append((i, s, delta_pct))

        prev_ratio = normalized_ratio
        results.append((s, normalized_ratio))

    return {
        "steps_count": len(scale_steps),
        "passed": len(failed_steps) == 0,
        "failed_steps": failed_steps,
    }


def evaluate_overhang_bounds(ascent, descent, negative_bearing, advance_width):
    total_ink_width = advance_width + abs(negative_bearing)
    total_ink_height = abs(ascent) + abs(descent)
    metrics_clipped = negative_bearing < 0.0 or total_ink_width > advance_width
    return {
        "ink_width": total_ink_width,
        "ink_height": total_ink_height,
        "metrics_clipped": metrics_clipped,
    }


def main():
    print("=== Text Torture Test Harness ===")
    steps = generate_scale_steps()
    print(f"Generated {len(steps)} scale steps from {steps[0]}x to {steps[-1]}x.")

    ideal_run = evaluate_scale_sweep(steps)
    print(f"Standard linear scale sweep passed: {ideal_run['passed']} (failures: {len(ideal_run['failed_steps'])})")

    jump_run = evaluate_scale_sweep(steps, artificial_jump_at=20)
    print(f"Simulated hinting/bold jump detected: {not jump_run['passed']} (detected jumps: {len(jump_run['failed_steps'])})")

    overhang = evaluate_overhang_bounds(ascent=-24.0, descent=8.0, negative_bearing=-3.5, advance_width=50.0)
    print(f"Overhang clipping test: bounds expanded = {overhang['metrics_clipped']}, ink width = {overhang['ink_width']}")

    print("Text torture harness active.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
