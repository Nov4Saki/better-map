"""
Generate 512x512 Full-Resolution Slippy Tiles from Master Map
=============================================================
Enables instantaneous, 60fps zooming down to 100% 1:1 pixel scale
without browser memory limits or blurriness.
"""

import os
import time
import shutil
import cv2
import numpy as np

def main():
    print("=" * 68)
    print("   GENERATING FULL-RESOLUTION 512×512 TILES")
    print("=" * 68)

    tiles_dir = os.path.abspath('tiles')
    os.makedirs(tiles_dir, exist_ok=True)

    print("Loading full master map (stitched_map.jpg)...")
    t0 = time.time()
    im = cv2.imread('stitched_map.jpg')
    h, w = im.shape[:2]
    print(f"Master dimensions: {w} × {h} pixels ({time.time() - t0:.1f}s)")

    TILE_SIZE = 512
    num_rows = int(np.ceil(h / float(TILE_SIZE)))
    num_cols = int(np.ceil(w / float(TILE_SIZE)))
    total_tiles = num_rows * num_cols

    print(f"Generating {total_tiles} tiles ({num_cols} cols × {num_rows} rows)...")
    t_start = time.time()
    saved = 0

    for r in range(num_rows):
        y0 = r * TILE_SIZE
        y1 = min(h, (r + 1) * TILE_SIZE)
        for c in range(num_cols):
            x0 = c * TILE_SIZE
            x1 = min(w, (c + 1) * TILE_SIZE)

            tile = im[y0:y1, x0:x1]
            # Pad if on edge
            if tile.shape[0] < TILE_SIZE or tile.shape[1] < TILE_SIZE:
                padded = np.zeros((TILE_SIZE, TILE_SIZE, 3), dtype=np.uint8)
                padded[:tile.shape[0], :tile.shape[1]] = tile
                tile = padded

            tile_path = os.path.join(tiles_dir, f"tile_{r}_{c}.jpg")
            cv2.imwrite(tile_path, tile, [cv2.IMWRITE_JPEG_QUALITY, 88])
            saved += 1

        if (r + 1) % 10 == 0 or r == num_rows - 1:
            elapsed = time.time() - t_start
            print(f"  Processed row {r + 1}/{num_rows} ({saved}/{total_tiles} tiles, {elapsed:.1f}s)")

    # Clean up test_tiles if exists
    if os.path.exists('test_tiles'):
        shutil.rmtree('test_tiles', ignore_errors=True)

    print("\n" + "=" * 68)
    print(f"SUCCESS: Generated {saved} full-res tiles in {time.time() - t_start:.1f}s!")
    print(f"Tiles saved to: {tiles_dir}")
    print("=" * 68)

if __name__ == '__main__':
    main()
