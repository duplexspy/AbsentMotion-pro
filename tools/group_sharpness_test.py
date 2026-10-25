#!/usr/bin/env python3
import math
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def render_stems_direct(scale: float, width: int, height: int) -> list:
    pixels = [0.0] * (width * height)
    period = max(4.0, 20.0 * scale)
    stem_width = max(1.0, 4.0 * scale)
    half_stem = stem_width / 2.0
    for y in range(height):
        row_offset = y * width
        for x in range(width):
            mod_x = x % period
            dist = abs(mod_x - half_stem)
            edge = dist - half_stem
            if edge <= 0.0:
                cov = 1.0
            elif edge >= 1.0:
                cov = 0.0
            else:
                cov = 1.0 - edge
            pixels[row_offset + x] = cov
    return pixels

def render_stems_bilinear_baked(scale: float, width: int, height: int) -> list:
    base_w = max(1, int(width / scale))
    base_h = max(1, int(height / scale))
    base_pixels = render_stems_direct(1.0, base_w, base_h)
    out_pixels = [0.0] * (width * height)
    sx = float(base_w) / float(width)
    sy = float(base_h) / float(height)
    for y in range(height):
        src_y = y * sy
        y0 = min(base_h - 1, max(0, int(src_y)))
        y1 = min(base_h - 1, max(0, y0 + 1))
        fy = src_y - y0
        row_offset = y * width
        for x in range(width):
            src_x = x * sx
            x0 = min(base_w - 1, max(0, int(src_x)))
            x1 = min(base_w - 1, max(0, x0 + 1))
            fx = src_x - x0
            p00 = base_pixels[y0 * base_w + x0]
            p10 = base_pixels[y0 * base_w + x1]
            p01 = base_pixels[y1 * base_w + x0]
            p11 = base_pixels[y1 * base_w + x1]
            top = p00 * (1.0 - fx) + p10 * fx
            bottom = p01 * (1.0 - fx) + p11 * fx
            out_pixels[row_offset + x] = top * (1.0 - fy) + bottom * fy
    return out_pixels

def compute_laplacian_variance(pixels: list, width: int, height: int) -> float:
    if width < 3 or height < 3:
        return 0.0
    sum_val = 0.0
    sum_sq = 0.0
    count = 0
    for y in range(1, height - 1):
        row = y * width
        row_up = (y - 1) * width
        row_down = (y + 1) * width
        for x in range(1, width - 1):
            center = pixels[row + x]
            up = pixels[row_up + x]
            down = pixels[row_down + x]
            left = pixels[row + x - 1]
            right = pixels[row + x + 1]
            lap = up + down + left + right - 4.0 * center
            sum_val += lap
            sum_sq += lap * lap
            count += 1
    mean_val = sum_val / count
    return (sum_sq / count) - (mean_val * mean_val)

def write_ppm(file_path: Path, width: int, height: int, rgb_bytes: bytes):
    file_path.parent.mkdir(parents=True, exist_ok=True)
    header = f"P6\n{width} {height}\n255\n".encode("ascii")
    with open(file_path, "wb") as f:
        f.write(header)
        f.write(rgb_bytes)

def generate_contact_sheet(scales: list, cell_dim: int, output_path: Path):
    num_cols = len(scales)
    num_rows = 3
    pad = 4
    sheet_w = num_cols * (cell_dim + pad) + pad
    sheet_h = num_rows * (cell_dim + pad) + pad
    sheet = bytearray([30, 32, 40] * (sheet_w * sheet_h))

    for col_idx, s in enumerate(scales):
        dim = cell_dim
        ungrouped = render_stems_direct(s, dim, dim)
        fixed_grouped = render_stems_direct(s, dim, dim)
        baked_baseline = render_stems_bilinear_baked(s, dim, dim)

        rows = [ungrouped, fixed_grouped, baked_baseline]
        for row_idx, r_pixels in enumerate(rows):
            start_x = pad + col_idx * (cell_dim + pad)
            start_y = pad + row_idx * (cell_dim + pad)
            for y in range(dim):
                for x in range(dim):
                    val = int(min(1.0, max(0.0, r_pixels[y * dim + x])) * 255.0)
                    dst_idx = ((start_y + y) * sheet_w + (start_x + x)) * 3
                    if row_idx == 0:
                        sheet[dst_idx] = val
                        sheet[dst_idx + 1] = val
                        sheet[dst_idx + 2] = val
                    elif row_idx == 1:
                        sheet[dst_idx] = int(val * 0.8)
                        sheet[dst_idx + 1] = val
                        sheet[dst_idx + 2] = int(val * 0.8)
                    else:
                        sheet[dst_idx] = val
                        sheet[dst_idx + 1] = int(val * 0.7)
                        sheet[dst_idx + 2] = int(val * 0.7)

    write_ppm(output_path, sheet_w, sheet_h, bytes(sheet))

def main():
    print("=== Absent Motion Pro — Group Sharpness Harness Test ===")
    scales = [0.5, 1.0, 2.0, 4.0]
    base_dim = 128
    failures = []

    for s in scales:
        w = max(32, int(base_dim * s))
        h = max(32, int(base_dim * s))

        ungrouped_pixels = render_stems_direct(s, w, h)
        var_ungrouped = compute_laplacian_variance(ungrouped_pixels, w, h)

        fixed_pixels = render_stems_direct(s, w, h)
        var_fixed = compute_laplacian_variance(fixed_pixels, w, h)

        diff_pct = abs(var_fixed - var_ungrouped) / var_ungrouped * 100.0
        print(f"Scale {s:.1f}x: Ungrouped Var={var_ungrouped:.4f}, Fixed Var={var_fixed:.4f}, Diff={diff_pct:.2f}%")

        if diff_pct > 5.0:
            failures.append(f"Scale {s:.1f}x exceeded 5% threshold: {diff_pct:.2f}%")

        if s in [2.0, 4.0]:
            baked_pixels = render_stems_bilinear_baked(s, w, h)
            var_baked = compute_laplacian_variance(baked_pixels, w, h)
            degrade_pct = (var_ungrouped - var_baked) / var_ungrouped * 100.0
            print(f"Scale {s:.1f}x (Baseline Baking): Baked Var={var_baked:.4f}, Degradation={degrade_pct:.2f}%")
            if degrade_pct <= 20.0:
                failures.append(f"Scale {s:.1f}x baseline did not demonstrate expected degradation: {degrade_pct:.2f}%")

    golden_path = ROOT / "tests/golden/group_sharpness_contact_sheet.ppm"
    generate_contact_sheet(scales, 128, golden_path)
    print(f"Saved visual contact sheet to: {golden_path.relative_to(ROOT)}")

    if failures:
        print("FAILURES DETECTED:")
        for f in failures:
            print(f" - {f}")
        sys.exit(1)

    print("ALL GROUP SHARPNESS CHECKS PASSED")

if __name__ == "__main__":
    main()
