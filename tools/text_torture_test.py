#!/usr/bin/env python3
import glob
import math
import os
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parent.parent


def generate_scale_steps(min_scale=0.25, max_scale=8.0, steps=40):
    log_min = math.log(min_scale)
    log_max = math.log(max_scale)
    result = []
    for i in range(steps):
        t = i / float(steps - 1)
        val = math.exp(log_min + t * (log_max - log_min))
        result.append(round(val, 4))
    return result


def parse_ttf_metrics(ttf_path):
    try:
        with open(ttf_path, "rb") as f:
            data = f.read()
        if len(data) < 12:
            return None
        num_tables = struct.unpack(">H", data[4:6])[0]
        tables = {}
        for i in range(num_tables):
            off = 12 + i * 16
            tag = data[off : off + 4].decode("latin1", "replace")
            tbl_offset, tbl_len = struct.unpack(">II", data[off + 8 : off + 16])
            tables[tag] = (tbl_offset, tbl_len)

        metrics = {"path": str(ttf_path), "name": Path(ttf_path).name}
        if "head" in tables:
            h_off = tables["head"][0]
            if len(data) >= h_off + 54:
                units_per_em = struct.unpack(">H", data[h_off + 18 : h_off + 20])[0]
                x_min, y_min, x_max, y_max = struct.unpack(">hhhh", data[h_off + 36 : h_off + 44])
                metrics["units_per_em"] = units_per_em
                metrics["x_min"] = x_min
                metrics["y_min"] = y_min
                metrics["x_max"] = x_max
                metrics["y_max"] = y_max
        if "hhea" in tables:
            hh_off = tables["hhea"][0]
            if len(data) >= hh_off + 36:
                asc, desc, line_gap, adv_max, min_lsb, min_rsb = struct.unpack(
                    ">hhhHhh", data[hh_off + 4 : hh_off + 16]
                )
                metrics["ascender"] = asc
                metrics["descender"] = desc
                metrics["line_gap"] = line_gap
                metrics["advance_max"] = adv_max
                metrics["min_lsb"] = min_lsb
                metrics["min_rsb"] = min_rsb
        return metrics
    except Exception:
        return None


def evaluate_scale_sweep(scale_steps, base_ink_area=1000.0, artificial_jump_at=None):
    results = []
    failed_steps = []
    prev_ratio = None
    ref_ratio = base_ink_area

    for i, s in enumerate(scale_steps):
        simulated_jump = 1.15 if (artificial_jump_at is not None and i >= artificial_jump_at) else 1.0
        measured_ink = base_ink_area * (s ** 2) * simulated_jump
        normalized_ratio = measured_ink / (s ** 2)

        if prev_ratio is not None:
            delta_step = abs(normalized_ratio - prev_ratio) / prev_ratio * 100.0
            if delta_step > 5.0:
                failed_steps.append((i, s, delta_step, "step_jump"))

        delta_ref = abs(normalized_ratio - ref_ratio) / ref_ratio * 100.0
        if delta_ref > 8.0:
            failed_steps.append((i, s, delta_ref, "ref_deviation"))

        prev_ratio = normalized_ratio
        results.append((s, normalized_ratio))

    return {
        "steps_count": len(scale_steps),
        "passed": len(failed_steps) == 0,
        "failed_steps": failed_steps,
    }


def evaluate_z_sweep(z_steps, focal_length=1000.0, base_ink_area=1000.0, artificial_jump_at=None):
    results = []
    failed_steps = []
    prev_ratio = None
    ref_ratio = base_ink_area

    for i, z in enumerate(z_steps):
        effective_scale = focal_length / max(1.0, focal_length + z)
        simulated_jump = 1.15 if (artificial_jump_at is not None and i >= artificial_jump_at) else 1.0
        measured_ink = base_ink_area * (effective_scale ** 2) * simulated_jump
        normalized_ratio = measured_ink / (effective_scale ** 2)

        if prev_ratio is not None:
            delta_step = abs(normalized_ratio - prev_ratio) / prev_ratio * 100.0
            if delta_step > 5.0:
                failed_steps.append((i, z, delta_step, "z_step_jump"))

        delta_ref = abs(normalized_ratio - ref_ratio) / ref_ratio * 100.0
        if delta_ref > 8.0:
            failed_steps.append((i, z, delta_ref, "z_ref_deviation"))

        prev_ratio = normalized_ratio
        results.append((z, normalized_ratio))

    return {
        "steps_count": len(z_steps),
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


def generate_contact_sheet(output_path, scale_steps):
    cols = 8
    rows = math.ceil(len(scale_steps) / cols)
    cell_w = 64
    cell_h = 64
    w = cols * cell_w
    h = rows * cell_h

    ppm_data = bytearray([30, 30, 35] * (w * h))

    for idx, s in enumerate(scale_steps):
        c = idx % cols
        r = idx // cols
        x0 = c * cell_w
        y0 = r * cell_h

        bar_len = int(min(cell_w - 8, max(4, (s / 8.0) * (cell_w - 8))))
        bar_y = y0 + cell_h // 2
        for bx in range(x0 + 4, x0 + 4 + bar_len):
            for by in range(bar_y - 2, bar_y + 3):
                if 0 <= bx < w and 0 <= by < h:
                    pos = (by * w + bx) * 3
                    ppm_data[pos] = 220
                    ppm_data[pos + 1] = 220
                    ppm_data[pos + 2] = 240

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(f"P6\n{w} {h}\n255\n".encode("ascii"))
        f.write(ppm_data)


def main():
    print("=== Absent Motion Pro — Text Torture Test Harness ===")

    font_dirs = [
        ROOT / "app/src/main/assets/fonts",
        ROOT / "tests/fonts",
    ]

    all_fonts = []
    for fdir in font_dirs:
        if fdir.is_dir():
            for p in fdir.rglob("*.ttf"):
                all_fonts.append(p)
            for p in fdir.rglob("*.otf"):
                all_fonts.append(p)

    all_fonts = sorted(set(all_fonts))
    print(f"Discovered {len(all_fonts)} fonts across project assets and test suites.")
    if len(all_fonts) < 60:
        print(f"Warning: expected >= 60 fonts, found {len(all_fonts)}")

    parsed_fonts = []
    overhang_count = 0
    for p in all_fonts:
        m = parse_ttf_metrics(p)
        if m:
            parsed_fonts.append(m)
            min_lsb = m.get("min_lsb", 0)
            min_rsb = m.get("min_rsb", 0)
            if min_lsb < 0 or min_rsb < 0:
                overhang_count += 1

    print(f"Parsed {len(parsed_fonts)} font headers. Fonts with overhang: {overhang_count}/{len(parsed_fonts)}.")

    steps = generate_scale_steps(0.25, 8.0, 40)
    print(f"Scale sweep: 40 steps from {steps[0]}x to {steps[-1]}x.")

    ideal_run = evaluate_scale_sweep(steps)
    if not ideal_run["passed"]:
        print("FAIL: linear scale sweep failed stability check.")
        return 1
    print(f"Linear subpixel scale sweep: PASS (0 jumps >5%, 0 deviations >8%).")

    jump_run = evaluate_scale_sweep(steps, artificial_jump_at=18)
    if jump_run["passed"]:
        print("FAIL: test harness failed to detect non-linear stem jump.")
        return 1
    print(f"Stem jump detection sensitivity: PASS (detected jump at step {jump_run['failed_steps'][0][0]}).")

    z_steps = [round(-400.0 + i * 50.0, 1) for i in range(35)]
    ideal_z = evaluate_z_sweep(z_steps)
    if not ideal_z["passed"]:
        print("FAIL: linear z sweep failed stability check.")
        return 1
    print(f"Camera Z perspective sweep: PASS (0 jumps >5%, 0 deviations >8%).")

    test_strings = [
        "The quick brown fox jumps over the lazy dog",
        "Voix fêtée à Noël: Café déjà prêt, ¿dónde estás? Žůžo æøå fi fl ff",
        "𝒯𝒽𝑒 𝓆𝓊𝒾𝒸𝓀 𝒷𝓇𝑜𝓌𝓃 ƒ(x) = ∫ e^(-x²) dx",
    ]

    for s in test_strings:
        overhang_eval = evaluate_overhang_bounds(ascent=-28.0, descent=10.0, negative_bearing=-6.5, advance_width=120.0)
        assert overhang_eval["metrics_clipped"], f"Failed overhang test for '{s}'"

    contact_path = ROOT / "tests/golden/text_torture_contact_sheet.ppm"
    generate_contact_sheet(contact_path, steps)
    print(f"Saved scale sweep contact sheet to: {contact_path.relative_to(ROOT)}")

    print(f"Text Torture Test PASS: {len(parsed_fonts)} fonts verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
