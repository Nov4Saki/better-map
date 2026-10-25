"""
Minecraft Map Automation Screenshot Bot (v2.3)
==============================================
Automatically navigates across a Minecraft map, detects map edges (light blue sky)
or physical pitch limits, presses F2 to take screenshots without skipping any areas,
and covers the entire map in a complete serpentine pattern.

Timing Cycle (as requested):
  mouse move -> 0.3s cooldown -> F2 screenshot -> immediately mouse move (no delay)

Hotkeys:
  [F7]  - Test Mouse Move (moves down 480px without click)
  [F6]  - Test Mouse Drag (click & drag down 480px)
  [F8]  - Start Full Automated Serpentine Scan
  [F9]  - Auto-Calibrate Mouse Sensitivity
  [F10] - Toggle Drag Mode (Click-Drag vs Pure Move)
  [F12] / [ESC] - Emergency Stop / Abort
"""

import os
import sys
import glob
import time
import shutil
import ctypes
from ctypes import wintypes
import winsound
import numpy as np
import cv2

# Windows API
user32 = ctypes.windll.user32
winmm = ctypes.windll.winmm

# Set Windows timer resolution to 1ms for sub-millisecond precision
winmm.timeBeginPeriod(1)

# PyDirectInput support
try:
    import pydirectinput
    pydirectinput.FAILSAFE = False
    pydirectinput.PAUSE = 0.01
except ImportError:
    pydirectinput = None

# Virtual key codes
VK_F2 = 0x71
VK_F6 = 0x75
VK_F7 = 0x76
VK_F8 = 0x77
VK_F9 = 0x78
VK_F10 = 0x79
VK_F12 = 0x7B
VK_ESCAPE = 0x1B

# Hardware scancodes
SCANCODE_F2 = 0x3C

# Mouse event constants
MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004

# ==============================================================================
# CONFIGURATION
# ==============================================================================
TARGET_STEP_Y_PX = 200      # Vertical movement per screenshot (~65% overlap on 1080p)
TARGET_STEP_X_PX = 450      # Horizontal movement between columns (~55% overlap on 1920p)
PIXELS_PER_MOUSE_UNIT = 1.0
DRAG_WITH_CLICK = False     # Set to True if map requires holding left-click
COOLDOWN_BEFORE_SHOT = 0.30 # Cooldown between mouse move and screenshot (0.3s)
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'new_screenshots')

# Specific user screenshot folder
PREFERRED_SCREENSHOTS_DIR = r"C:\Users\novas\curseforge\minecraft\Instances\Fabulously Optimized (5)\screenshots"

# ==============================================================================
# AUDIO NOTIFICATIONS
# ==============================================================================
def sound_start():
    winsound.Beep(800, 100)
    winsound.Beep(1200, 150)

def sound_capture():
    winsound.Beep(1600, 40)

def sound_turn():
    winsound.Beep(700, 80)
    winsound.Beep(1000, 100)

def sound_finish():
    winsound.Beep(600, 120)
    winsound.Beep(900, 120)
    winsound.Beep(1400, 300)

def sound_abort():
    winsound.Beep(350, 300)

def sound_test():
    winsound.Beep(1100, 80)

# ==============================================================================
# INPUT ACTIONS
# ==============================================================================
def is_key_pressed(vk_code):
    return (user32.GetAsyncKeyState(vk_code) & 0x8000) != 0

def press_f2():
    """Simulate Minecraft F2 screenshot keypress."""
    if pydirectinput is not None:
        try:
            pydirectinput.press('f2')
            return
        except Exception:
            pass

    user32.keybd_event(VK_F2, SCANCODE_F2, 0, 0)
    time.sleep(0.06)
    user32.keybd_event(VK_F2, SCANCODE_F2, 2, 0)

def send_mouse_raw(dx, dy, flags=MOUSEEVENTF_MOVE):
    """Sends relative mouse movement to Minecraft."""
    if pydirectinput is not None:
        try:
            if flags & MOUSEEVENTF_LEFTDOWN:
                pydirectinput.mouseDown()
            elif flags & MOUSEEVENTF_LEFTUP:
                pydirectinput.mouseUp()
            elif flags & MOUSEEVENTF_MOVE and (dx != 0 or dy != 0):
                pydirectinput.moveRel(int(dx), int(dy), relative=True, _pause=False)
            return
        except Exception:
            pass

    user32.mouse_event(flags, int(dx), int(dy), 0, 0)

def move_mouse(dx_units, dy_units, steps=15, duration=0.15):
    """Send relative mouse movement to Minecraft smoothly with subpixel accumulator."""
    if DRAG_WITH_CLICK:
        send_mouse_raw(0, 0, MOUSEEVENTF_LEFTDOWN)
        time.sleep(0.02)

    step_dx = float(dx_units) / steps
    step_dy = float(dy_units) / steps
    interval = max(0.005, duration / steps)

    acc_x = 0.0
    acc_y = 0.0

    for _ in range(steps):
        acc_x += step_dx
        acc_y += step_dy
        mx = int(round(acc_x))
        my = int(round(acc_y))
        if mx != 0 or my != 0:
            send_mouse_raw(mx, my, MOUSEEVENTF_MOVE)
            acc_x -= mx
            acc_y -= my
        time.sleep(interval)

    # Flush remainder
    rx = int(round(acc_x))
    ry = int(round(acc_y))
    if rx != 0 or ry != 0:
        send_mouse_raw(rx, ry, MOUSEEVENTF_MOVE)

    if DRAG_WITH_CLICK:
        time.sleep(0.02)
        send_mouse_raw(0, 0, MOUSEEVENTF_LEFTUP)

# ==============================================================================
# DIRECTORY & SETTINGS VALIDATION
# ==============================================================================
def check_minecraft_environment():
    """Validates screenshot folder and checks rawMouseInput in options.txt."""
    folder = PREFERRED_SCREENSHOTS_DIR
    if not os.path.exists(folder):
        import psutil
        candidates = []
        for p in psutil.process_iter(['name', 'cmdline']):
            try:
                if 'java' in (p.info['name'] or '').lower():
                    cmd = p.info.get('cmdline') or []
                    for idx, arg in enumerate(cmd):
                        if arg == '--gameDir' and idx + 1 < len(cmd):
                            sdir = os.path.join(cmd[idx + 1], 'screenshots')
                            if os.path.exists(sdir):
                                candidates.append(sdir)
            except Exception:
                pass
        if candidates:
            folder = candidates[0]
        else:
            folder = os.path.expandvars(r"%APPDATA%\.minecraft\screenshots")

    instance_dir = os.path.dirname(folder)
    options_path = os.path.join(instance_dir, 'options.txt')
    raw_input_is_on = False

    if os.path.exists(options_path):
        try:
            with open(options_path, 'r', encoding='utf-8') as f:
                content = f.read()
            if 'rawMouseInput:true' in content:
                raw_input_is_on = True
                new_content = content.replace('rawMouseInput:true', 'rawMouseInput:false')
                with open(options_path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
        except Exception:
            pass

    return folder, raw_input_is_on

def wait_for_new_screenshot(folder, known_files, timeout=4.0):
    """Wait for Minecraft to save the new F2 screenshot to disk."""
    start = time.time()
    while time.time() - start < timeout:
        if is_key_pressed(VK_F12) or is_key_pressed(VK_ESCAPE):
            return None
        current = set(glob.glob(os.path.join(folder, '*.png')))
        new_files = current - known_files
        if new_files:
            newest = max(new_files, key=os.path.getmtime)
            prev_size = -1
            for _ in range(12):
                try:
                    size = os.path.getsize(newest)
                    if size > 1000 and size == prev_size:
                        return newest
                    prev_size = size
                except Exception:
                    pass
                time.sleep(0.04)
            return newest
        time.sleep(0.04)
    return None

# ==============================================================================
# EDGE DETECTION (Robust Centered Border Analysis)
# ==============================================================================
def detect_map_edges(im):
    """
    Robust edge detector that isolates true map boundaries.
    Excludes corners to prevent bottom/top void from causing false horizontal edge detections.
    """
    if im is None:
        return {'top': False, 'bottom': False, 'left': False, 'right': False, 'all_sky': False}

    h, w = im.shape[:2]
    b, g, r = im[:, :, 0], im[:, :, 1], im[:, :, 2]
    sky = (b > 235) & (np.abs(g.astype(int) - 208) < 30) & (np.abs(r.astype(int) - 181) < 30)

    # Whole frame sky percentage
    all_sky = float(np.mean(sky)) > 0.75

    # 1. Top & Bottom: sample center 60% of width (excluding corners)
    top_strip = sky[:40, int(w * 0.20):int(w * 0.80)]
    bot_strip = sky[-40:, int(w * 0.20):int(w * 0.80)]
    top_edge = float(np.mean(top_strip)) > 0.40
    bot_edge = float(np.mean(bot_strip)) > 0.40

    # 2. Left & Right: sample middle 40% of height (strictly between Y=30% and Y=70%)
    left_strip = sky[int(h * 0.30):int(h * 0.70), :40]
    right_strip = sky[int(h * 0.30):int(h * 0.70), -40:]
    left_edge = float(np.mean(left_strip)) > 0.40
    right_edge = float(np.mean(right_strip)) > 0.40

    # CRITICAL: If top or bottom boundary is active, ignore right/left edge to prevent false triggers!
    if top_edge or bot_edge:
        right_edge = False

    return {
        'top': top_edge,
        'bottom': bot_edge,
        'left': left_edge,
        'right': right_edge,
        'all_sky': all_sky
    }

# ==============================================================================
# SENSITIVITY CALIBRATION
# ==============================================================================
def calibrate_sensitivity(mc_folder):
    print("\n--- SENSITIVITY CALIBRATION ---")
    print("Switch to Minecraft! Calibration will start in 3 seconds...")
    for sec in range(3, 0, -1):
        print(f"Starting in {sec}s...")
        winsound.Beep(700, 100)
        time.sleep(1.0)

    known = set(glob.glob(os.path.join(mc_folder, '*.png')))
    press_f2()
    img1_path = wait_for_new_screenshot(mc_folder, known)
    if not img1_path:
        print("[!] Could not capture first calibration screenshot.")
        return 1.0

    known.add(img1_path)
    test_delta = 250
    print(f"Moving mouse by {test_delta} units...")
    move_mouse(0, test_delta)
    time.sleep(COOLDOWN_BEFORE_SHOT)

    press_f2()
    img2_path = wait_for_new_screenshot(mc_folder, known)
    if not img2_path:
        print("[!] Could not capture second calibration screenshot.")
        return 1.0

    im1 = cv2.imread(img1_path, cv2.IMREAD_GRAYSCALE)
    im2 = cv2.imread(img2_path, cv2.IMREAD_GRAYSCALE)
    h, w = im1.shape

    patch = im1[int(h*0.3):int(h*0.6), int(w*0.3):int(w*0.6)]
    res = cv2.matchTemplate(im2, patch, cv2.TM_CCOEFF_NORMED)
    _, _, _, max_loc = cv2.minMaxLoc(res)

    dy_measured = abs(max_loc[1] - int(h*0.3))
    ratio = dy_measured / float(test_delta)
    print(f"[OK] Displacement: {dy_measured}px for {test_delta} units (Ratio: {ratio:.3f} px/unit)")
    sound_turn()
    return max(0.2, ratio)

# ==============================================================================
# MAIN SCANNER
# ==============================================================================
def run_scanner():
    global PIXELS_PER_MOUSE_UNIT, DRAG_WITH_CLICK

    mc_folder, had_raw_input = check_minecraft_environment()
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("=" * 68)
    print("        MINECRAFT MAP AUTOMATION SCREENSHOT BOT (v2.3)")
    print("=" * 68)
    print(f"Active Minecraft Dir : {mc_folder}")
    print(f"Output Destination   : {OUTPUT_DIR}")
    print(f"Target Step Size     : Y={TARGET_STEP_Y_PX}px, X={TARGET_STEP_X_PX}px (>55% Overlap)")
    print(f"Timing Flow          : Move -> 0.3s Cooldown -> F2 -> Immediate Next Move")
    print(f"Drag Mode            : {'CLICK & DRAG' if DRAG_WITH_CLICK else 'PURE MOVE'}")
    print("-" * 68)

    if had_raw_input:
        print("*****************************************************************")
        print("[!] NOTICE: 'Raw Input' was detected in Minecraft settings.")
        print("    We set 'rawMouseInput:false' in options.txt.")
        print("    If Minecraft is currently running, please verify:")
        print("    In Minecraft: Options -> Controls -> Mouse Settings -> Raw Input: OFF")
        print("*****************************************************************")
        print("-" * 68)

    print("HOTKEYS & TEST CONTROLS (Press inside Minecraft):")
    print("  [F7]  - Test Mouse Move (moves down 480px without click)")
    print("  [F6]  - Test Mouse Drag (clicks and drags down 480px)")
    print("  [F8]  - START Full Automated Serpentine Scan")
    print("  [F9]  - Auto-Calibrate Mouse Sensitivity")
    print("  [F10] - Toggle Drag Mode (Click-Drag vs Pure Move)")
    print("  [F12] or [ESC] - EMERGENCY STOP / ABORT")
    print("-" * 68)
    print(">>> INSTRUCTIONS:")
    print(" 1. In Minecraft, align view to the TOP-LEFT corner of the map.")
    print(" 2. Press [F7] to test camera movement.")
    print(" 3. Press [F8] to start automated capture!")
    print("-" * 68)

    # Hotkey listener before starting scan
    while True:
        # Test Move
        if is_key_pressed(VK_F7):
            sound_test()
            print(f">> [F7] Testing Mouse Move (+{TARGET_STEP_Y_PX}px down)...")
            old_mode = DRAG_WITH_CLICK
            DRAG_WITH_CLICK = False
            move_mouse(0, int(TARGET_STEP_Y_PX / PIXELS_PER_MOUSE_UNIT))
            DRAG_WITH_CLICK = old_mode
            time.sleep(0.4)

        # Test Drag
        if is_key_pressed(VK_F6):
            sound_test()
            print(f">> [F6] Testing Mouse Drag (+{TARGET_STEP_Y_PX}px down with click)...")
            old_mode = DRAG_WITH_CLICK
            DRAG_WITH_CLICK = True
            move_mouse(0, int(TARGET_STEP_Y_PX / PIXELS_PER_MOUSE_UNIT))
            DRAG_WITH_CLICK = old_mode
            time.sleep(0.4)

        # Toggle Drag Mode
        if is_key_pressed(VK_F10):
            DRAG_WITH_CLICK = not DRAG_WITH_CLICK
            sound_turn()
            print(f">> [F10] Drag Mode is now: {'CLICK & DRAG' if DRAG_WITH_CLICK else 'PURE MOVE'}")
            time.sleep(0.4)

        # Auto-Calibrate
        if is_key_pressed(VK_F9):
            PIXELS_PER_MOUSE_UNIT = calibrate_sensitivity(mc_folder)
            print(f"\n[OK] Calibrated! 1 mouse unit = {PIXELS_PER_MOUSE_UNIT:.3f}px.")
            time.sleep(0.5)

        # Start Bot
        if is_key_pressed(VK_F8):
            break

        # Abort
        if is_key_pressed(VK_ESCAPE) or is_key_pressed(VK_F12):
            print("\nAborted.")
            return

        time.sleep(0.04)

    # Auto-archive existing files in output directory so fresh scan is clean
    existing_files = glob.glob(os.path.join(OUTPUT_DIR, '*.png'))
    if existing_files:
        timestamp_str = time.strftime("%Y%m%d_%H%M%S")
        backup_folder = os.path.join(os.path.dirname(OUTPUT_DIR), f"screenshots_backup_{timestamp_str}")
        os.makedirs(backup_folder, exist_ok=True)
        for ef in existing_files:
            shutil.move(ef, os.path.join(backup_folder, os.path.basename(ef)))
        print(f"[i] Moved {len(existing_files)} previous screenshots to backup folder: {backup_folder}")

    sound_start()
    print("\n[+] BOT STARTED! Serpentine scan beginning from top-left...")

    known_screenshots = set(glob.glob(os.path.join(mc_folder, '*.png')))
    direction = "DOWN"
    column = 1
    total_captured = 0
    consecutive_right_hits = 0
    prev_image = None
    first_shot_in_column = True

    mouse_step_y = int(round(TARGET_STEP_Y_PX / PIXELS_PER_MOUSE_UNIT))
    mouse_step_x = int(round(TARGET_STEP_X_PX / PIXELS_PER_MOUSE_UNIT))

    while True:
        if is_key_pressed(VK_F12) or is_key_pressed(VK_ESCAPE):
            sound_abort()
            print("\n[!] EMERGENCY STOP triggered. Bot stopped.")
            break

        # -------------------------------------------------------------
        # STEP 1: TAKE SCREENSHOT (after 0.3s cooldown if movement occurred)
        # -------------------------------------------------------------
        press_f2()
        shot_path = wait_for_new_screenshot(mc_folder, known_screenshots)

        if not shot_path:
            sound_test()
            print("[!] Screenshot not detected in 4s. Please ensure Minecraft is focused and active!")
            print(f"    Watching folder: {mc_folder}")
            print("    Retrying screenshot in 2s (or press [ESC] to stop)...")
            time.sleep(2.0)
            continue

        known_screenshots.add(shot_path)
        total_captured += 1
        sound_capture()

        # Load image for real-time edge and movement verification
        cur_image = cv2.imread(shot_path)

        # Copy to project folder
        dest_filename = f"map_col{column:02d}_{total_captured:03d}_{direction.lower()}.png"
        dest_path = os.path.join(OUTPUT_DIR, dest_filename)
        shutil.copy2(shot_path, dest_path)

        # Check movement delta against previous frame (detect pitch clamp / physical limit)
        is_stationary = False
        pixel_diff = 100.0
        if prev_image is not None and not first_shot_in_column:
            pixel_diff = float(np.mean(cv2.absdiff(cur_image, prev_image)))
            if pixel_diff < 3.0:
                is_stationary = True

        prev_image = cur_image
        first_shot_in_column = False

        # Detect Edges
        edges = detect_map_edges(cur_image)
        print(f"[{total_captured:03d}] Col {column:02d} | Dir: {direction:4s} | Saved: {dest_filename} | Edges: T={edges['top']}, B={edges['bottom']}, R={edges['right']} | Motion: diff={pixel_diff:.1f}")

        # -------------------------------------------------------------
        # STEP 2: CHECK RIGHT-EDGE COMPLETION
        # -------------------------------------------------------------
        if column >= 2 and edges['all_sky']:
            sound_finish()
            print("\n" + "=" * 68)
            print("[SUCCESS] STEPPED BEYOND MAP BORDER (EMPTY SKY DETECTED)! MAP COMPLETE!")
            print(f"Total screenshots captured : {total_captured}")
            print(f"Total columns traversed    : {column}")
            print(f"Saved to                   : {OUTPUT_DIR}")
            print("=" * 68)
            break

        if column >= 2 and edges['right'] and not edges['top'] and not edges['bottom']:
            consecutive_right_hits += 1
            if consecutive_right_hits >= 3:
                sound_finish()
                print("\n" + "=" * 68)
                print("[SUCCESS] RIGHT EDGE OF MAP REACHED! MAP FULLY COVERED!")
                print(f"Total screenshots captured : {total_captured}")
                print(f"Total columns traversed    : {column}")
                print(f"Saved to                   : {OUTPUT_DIR}")
                print("=" * 68)
                break
        else:
            consecutive_right_hits = 0

        # -------------------------------------------------------------
        # STEP 3: MOVEMENT NAVIGATION & IMMEDIATE NEXT MOVE (NO POST-DELAY)
        # -------------------------------------------------------------
        if direction == "DOWN":
            # Reached bottom by blue sky OR camera pitch clamp (+90 deg reached)
            if edges['bottom'] or is_stationary:
                sound_turn()
                reason = "blue sky" if edges['bottom'] else "camera pitch limit reached"
                print(f"  --> Reached BOTTOM ({reason}). Stepping RIGHT to Column {column + 1}...")
                move_mouse(mouse_step_x, 0)
                direction = "UP"
                column += 1
                prev_image = None
                first_shot_in_column = True
            else:
                move_mouse(0, mouse_step_y)

        elif direction == "UP":
            # Reached top by blue sky OR camera pitch clamp (-90 deg reached)
            if edges['top'] or is_stationary:
                sound_turn()
                reason = "blue sky" if edges['top'] else "camera pitch limit reached"
                print(f"  --> Reached TOP ({reason}). Stepping RIGHT to Column {column + 1}...")
                move_mouse(mouse_step_x, 0)
                direction = "DOWN"
                column += 1
                prev_image = None
                first_shot_in_column = True
            else:
                move_mouse(0, -mouse_step_y)

        # EXACT TIMING CYCLE:
        # Move happened immediately -> 0.3s cooldown -> F2 screenshot -> immediate next move!
        time.sleep(COOLDOWN_BEFORE_SHOT)

    print("\nScan complete! Run 'python stitch_new_map.py' to generate your new master map.")

if __name__ == '__main__':
    run_scanner()
