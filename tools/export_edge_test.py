#!/usr/bin/env python3
import sys

TEST_RESOLUTIONS = [
    (1080, 1920, "1080p Portrait"),
    (1920, 1080, "1080p Landscape"),
    (1080, 1350, "4:5 Social Portrait"),
    (3840, 2160, "4K UHD"),
    (1000, 563, "Custom Non-16 Aspect"),
    (719, 1279, "Odd Dimension Edge Case"),
]


def check_dimension_alignment(w, h, align=16):
    aligned_w = ((w + align - 1) // align) * align
    aligned_h = ((h + align - 1) // align) * align
    needs_adjustment = (w % align != 0) or (h % align != 0)
    pad_right = aligned_w - w
    pad_bottom = aligned_h - h
    return {
        "width": w,
        "height": h,
        "aligned_w": aligned_w,
        "aligned_h": aligned_h,
        "needs_adjustment": needs_adjustment,
        "pad_right": pad_right,
        "pad_bottom": pad_bottom,
    }


def analyze_edge_pixels(pixels, width, height, outer_rows=4, threshold=0.1):
    left_dark = 0
    right_dark = 0
    top_dark = 0
    bottom_dark = 0

    for y in range(height):
        for x in range(outer_rows):
            idx = (y * width + x) * 4
            lum = (pixels[idx] * 0.2126 + pixels[idx + 1] * 0.7152 + pixels[idx + 2] * 0.0722) / 255.0
            if lum < threshold:
                left_dark += 1
        for x in range(width - outer_rows, width):
            idx = (y * width + x) * 4
            lum = (pixels[idx] * 0.2126 + pixels[idx + 1] * 0.7152 + pixels[idx + 2] * 0.0722) / 255.0
            if lum < threshold:
                right_dark += 1

    for y in range(outer_rows):
        for x in range(width):
            idx = (y * width + x) * 4
            lum = (pixels[idx] * 0.2126 + pixels[idx + 1] * 0.7152 + pixels[idx + 2] * 0.0722) / 255.0
            if lum < threshold:
                top_dark += 1
    for y in range(height - outer_rows, height):
        for x in range(width):
            idx = (y * width + x) * 4
            lum = (pixels[idx] * 0.2126 + pixels[idx + 1] * 0.7152 + pixels[idx + 2] * 0.0722) / 255.0
            if lum < threshold:
                bottom_dark += 1

    return {
        "left_dark": left_dark,
        "right_dark": right_dark,
        "top_dark": top_dark,
        "bottom_dark": bottom_dark,
        "has_black_edge": (left_dark > 0 or right_dark > 0 or top_dark > 0 or bottom_dark > 0),
    }


def main():
    print("=== Export Edge Dimension Alignment Matrix ===")
    has_issues = False
    for w, h, label in TEST_RESOLUTIONS:
        res = check_dimension_alignment(w, h, 16)
        status = "NEEDS_ALIGNMENT" if res["needs_adjustment"] else "ALIGNED"
        print(f"[{status}] {label} ({w}x{h}) -> encoder aligned {res['aligned_w']}x{res['aligned_h']} (pad_x={res['pad_right']}, pad_y={res['pad_bottom']})")
        if res["needs_adjustment"]:
            has_issues = True

    print("\nAlignment matrix scan complete. Harness active.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
