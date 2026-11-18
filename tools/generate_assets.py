#!/usr/bin/env python3
"""Generates every texture, sound and JSON asset of the mod procedurally (original art, no copyrighted material).

Usage: python3 tools/generate_assets.py   (needs Pillow, numpy, scipy and ffmpeg)
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "spiderultimate"
A = os.path.join(ROOT, "assets", NS)
D = os.path.join(ROOT, "data", NS)


def mk(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def write_json(path, obj):
    with open(mk(path), "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# ----------------------------------------------------------------------------- palette
NAVY = (22, 30, 70, 255)
NAVY_DARK = (14, 20, 48, 255)
CRIMSON = (178, 28, 44, 255)
CRIMSON_DARK = (120, 16, 30, 255)
SILVER = (205, 215, 230, 255)
CYAN = (90, 220, 255, 255)
BLACK = (12, 12, 16, 255)
STEEL = (96, 104, 120, 255)
STEEL_DARK = (58, 64, 78, 255)
WHITE = (245, 248, 255, 255)


# ----------------------------------------------------------------------------- item icons (16x16)
def px(img, pts, col):
    for x, y in pts:
        if 0 <= x < img.width and 0 <= y < img.height:
            img.putpixel((x, y), col)


def rect(img, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, [(x, y)], col)


def outline(img):
    """Adds a dark outline around opaque pixels."""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and src.getpixel((nx, ny))[3] > 0:
                        img.putpixel((x, y), BLACK)
                        break


def icon_web_shooter():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rect(im, 3, 6, 11, 11, STEEL)           # gauntlet body
    rect(im, 3, 6, 11, 7, STEEL_DARK)
    rect(im, 4, 8, 10, 8, CRIMSON)           # red band
    rect(im, 4, 10, 10, 10, NAVY)            # blue band
    rect(im, 11, 7, 13, 9, STEEL_DARK)       # nozzle housing
    px(im, [(14, 8)], WHITE)                 # nozzle tip
    px(im, [(13, 8)], CYAN)
    rect(im, 5, 5, 8, 5, STEEL_DARK)         # top rail
    px(im, [(6, 12), (7, 12), (8, 12)], STEEL_DARK)
    px(im, [(5, 7)], SILVER)
    outline(im)
    return im


def web_lines(im, x0, y0, x1, y1):
    """Few silver web strands on a rectangular patch."""
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    for x in range(x0, x1 + 1):
        if (x - x0) % 3 == 0:
            px(im, [(x, cy)], SILVER)
    for y in range(y0, y1 + 1):
        if (y - y0) % 3 == 0:
            px(im, [(cx, y)], SILVER)


def icon_helmet():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rect(im, 3, 3, 12, 12, CRIMSON)
    rect(im, 3, 3, 12, 4, CRIMSON_DARK)
    rect(im, 3, 11, 5, 12, (0, 0, 0, 0))
    rect(im, 10, 11, 12, 12, (0, 0, 0, 0))
    rect(im, 5, 7, 7, 9, CYAN)               # lenses
    rect(im, 9, 7, 11, 9, CYAN)
    rect(im, 6, 8, 6, 8, WHITE)
    rect(im, 10, 8, 10, 8, WHITE)
    web_lines(im, 3, 3, 12, 6)
    outline(im)
    return im


def icon_chest():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rect(im, 2, 2, 13, 14, NAVY)
    rect(im, 0, 2, 2, 8, CRIMSON)
    rect(im, 13, 2, 15, 8, CRIMSON)
    rect(im, 5, 2, 10, 3, (0, 0, 0, 0))
    rect(im, 5, 2, 10, 2, NAVY_DARK)
    rect(im, 7, 5, 8, 9, SILVER)             # spider-ish emblem (abstract web star)
    rect(im, 5, 7, 10, 7, SILVER)
    px(im, [(6, 6), (9, 6), (6, 8), (9, 8)], CYAN)
    rect(im, 2, 12, 13, 14, CRIMSON_DARK)
    outline(im)
    return im


def icon_legs():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rect(im, 3, 2, 12, 5, CRIMSON_DARK)
    rect(im, 3, 6, 7, 14, NAVY)
    rect(im, 8, 6, 12, 14, NAVY)
    rect(im, 7, 6, 8, 14, (0, 0, 0, 0))
    rect(im, 3, 6, 4, 14, NAVY_DARK)
    rect(im, 11, 6, 12, 14, NAVY_DARK)
    px(im, [(5, 9), (10, 9)], SILVER)
    outline(im)
    return im


def icon_boots():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rect(im, 2, 6, 6, 12, CRIMSON)
    rect(im, 9, 6, 13, 12, CRIMSON)
    rect(im, 1, 11, 6, 13, CRIMSON_DARK)
    rect(im, 9, 11, 14, 13, CRIMSON_DARK)
    rect(im, 2, 6, 6, 7, NAVY)
    rect(im, 9, 6, 13, 7, NAVY)
    px(im, [(4, 9), (11, 9)], SILVER)
    outline(im)
    return im


# ----------------------------------------------------------------------------- armor layers (64x32)
def armor_texture(layer):
    """Suit texture in the classic 64x32 layout. Original design: navy body, crimson trim, silver web lines."""
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    def region(x0, y0, w, h, base, pattern=True):
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
        if pattern:
            for i in range(0, w, 3):
                d.line([x0 + i, y0, x0 + i, y0 + h - 1], fill=(base[0] + 14, base[1] + 14, base[2] + 18, 255))
            for j in range(0, h, 3):
                d.line([x0, y0 + j, x0 + w - 1, y0 + j], fill=(base[0] + 14, base[1] + 14, base[2] + 18, 255))

    if layer == 1:
        # head: 8x8 faces at (0,8) (8,8) (16,8) (24,8), top (8,0), bottom (16,0)
        for x0 in (0, 8, 16, 24):
            region(x0, 8, 8, 8, CRIMSON)
        region(8, 0, 8, 8, CRIMSON_DARK)
        region(16, 0, 8, 8, CRIMSON_DARK)
        d.rectangle([9, 11, 11, 13], fill=CYAN)     # lenses on the front face
        d.rectangle([12, 11, 14, 13], fill=CYAN)
        d.point([10, 12], fill=WHITE)
        d.point([13, 12], fill=WHITE)
        # body: top (20,16) 8x4, front (20,20) 8x12, back (32,20) 8x12, sides (16,20) and (28,20) 4x12
        region(20, 16, 8, 4, NAVY_DARK)
        region(28, 16, 8, 4, NAVY_DARK)
        region(20, 20, 8, 12, NAVY)
        region(32, 20, 8, 12, NAVY)
        region(16, 20, 4, 12, CRIMSON)
        region(28, 20, 4, 12, CRIMSON)
        d.rectangle([23, 22, 24, 27], fill=SILVER)  # emblem
        d.rectangle([21, 24, 26, 24], fill=SILVER)
        d.point([22, 23], fill=CYAN)
        d.point([25, 23], fill=CYAN)
        d.rectangle([20, 29, 27, 31], fill=CRIMSON_DARK)  # belt
        # arms (right arm layout, left arm mirrors it): top (44,16), bottom (48,16), faces at y=20
        region(44, 16, 4, 4, NAVY_DARK)
        region(48, 16, 4, 4, NAVY_DARK)
        for x0 in (40, 44, 48, 52):
            region(x0, 20, 4, 8, CRIMSON)
            region(x0, 28, 4, 4, NAVY_DARK)          # gloves / cuffs
        # boots (legs, lower part) on layer 1: right leg faces at (0,20) (4,20) (8,20) (12,20)
        for x0 in (0, 4, 8, 12):
            region(x0, 26, 4, 6, CRIMSON_DARK)
        region(4, 16, 4, 4, CRIMSON_DARK)
        region(8, 16, 4, 4, CRIMSON_DARK)
    else:
        # leggings layer: legs and the lower body
        region(4, 16, 4, 4, NAVY_DARK)
        region(8, 16, 4, 4, NAVY_DARK)
        for x0 in (0, 4, 8, 12):
            region(x0, 20, 4, 12, NAVY)
            d.line([x0, 20, x0 + 3, 20], fill=CRIMSON_DARK)
        for x0 in (0, 8):
            d.line([x0 + 1, 22, x0 + 1, 31], fill=CRIMSON)   # side stripe
        region(20, 16, 8, 4, NAVY_DARK)
        region(28, 16, 8, 4, NAVY_DARK)
        region(20, 20, 8, 12, NAVY)
        region(32, 20, 8, 12, NAVY)
        region(16, 20, 4, 12, NAVY_DARK)
        region(28, 20, 4, 12, NAVY_DARK)
        d.rectangle([20, 20, 27, 22], fill=CRIMSON_DARK)  # belt line
    return im


def icon_main():
    im = Image.new("RGBA", (128, 128), NAVY_DARK)
    d = ImageDraw.Draw(im)
    cx = cy = 64
    for r in (14, 28, 42, 56):
        pts = []
        for k in range(8):
            a = k * np.pi / 4
            rr = r * (0.82 if k % 2 else 1.0)
            pts.append((cx + rr * np.cos(a), cy + rr * np.sin(a)))
        d.polygon(pts, outline=SILVER)
    for k in range(8):
        a = k * np.pi / 4
        d.line([cx, cy, cx + 58 * np.cos(a), cy + 58 * np.sin(a)], fill=SILVER, width=1)
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=CRIMSON)
    d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=CYAN)
    return im


# ----------------------------------------------------------------------------- sounds
SR = 44100
rng = np.random.default_rng(1234)


def t_axis(sec):
    return np.linspace(0, sec, int(SR * sec), endpoint=False)


def env(n, attack=0.01, decay=6.0):
    t = np.linspace(0, 1, n)
    e = np.exp(-decay * t)
    a = max(1, int(attack * SR))
    e[:a] *= np.linspace(0, 1, a)
    return e


def lowpass(x, cutoff):
    from scipy.signal import butter, lfilter
    b, a = butter(2, cutoff / (SR / 2), btype="low")
    return lfilter(b, a, x)


def bandpass(x, lo, hi):
    from scipy.signal import butter, lfilter
    b, a = butter(2, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return lfilter(b, a, x)


def norm(x, peak=0.8):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def snd_web_shoot():
    t = t_axis(0.28)
    f = 500 + 2600 * (t / t[-1]) ** 1.6
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) * 0.5
    noise = bandpass(rng.standard_normal(len(t)), 1500, 7000) * 0.9
    return norm((tone + noise) * env(len(t), 0.004, 5.0))


def snd_web_hit():
    t = t_axis(0.2)
    thud = np.sin(2 * np.pi * (140 - 60 * t / t[-1]) * t) * env(len(t), 0.002, 14)
    splat = bandpass(rng.standard_normal(len(t)), 800, 5000) * env(len(t), 0.001, 20) * 0.6
    return norm(thud + splat)


def snd_web_swing():
    t = t_axis(0.7)
    n = bandpass(rng.standard_normal(len(t)), 250, 2200)
    shape = np.sin(np.pi * np.linspace(0, 1, len(t))) ** 2
    return norm(n * shape, 0.6)


def snd_web_land():
    t = t_axis(0.4)
    thump = np.sin(2 * np.pi * (95 - 55 * t / t[-1]) * t) * env(len(t), 0.002, 9)
    dust = lowpass(rng.standard_normal(len(t)), 2500) * env(len(t), 0.003, 10) * 0.7
    return norm(thump * 1.2 + dust)


def snd_spider_sense():
    t = t_axis(0.55)
    vib = 1 + 0.02 * np.sin(2 * np.pi * 24 * t)
    s = np.sin(2 * np.pi * 1760 * vib * t) + 0.7 * np.sin(2 * np.pi * 2349 * vib * t)
    s = s * (0.6 + 0.4 * np.sin(2 * np.pi * 18 * t))
    return norm(s * env(len(t), 0.01, 3.5), 0.6)


def snd_web_wings():
    t = t_axis(0.5)
    n = bandpass(rng.standard_normal(len(t)), 300, 3500)
    flutter = 0.55 + 0.45 * np.sin(2 * np.pi * 26 * t)
    shape = np.sin(np.pi * np.linspace(0, 1, len(t)))
    return norm(n * flutter * shape, 0.6)


def snd_web_shield():
    t = t_axis(0.6)
    mod = np.sin(2 * np.pi * 7 * t) * 6
    s = np.sin(2 * np.pi * 700 * t + mod) * 0.6 + np.sin(2 * np.pi * 1400 * t + mod * 0.5) * 0.3
    n = bandpass(rng.standard_normal(len(t)), 2000, 8000) * 0.25
    return norm((s + n) * env(len(t), 0.01, 4.0), 0.6)


SOUNDS = {
    "web_shoot": snd_web_shoot,
    "web_hit": snd_web_hit,
    "web_swing": snd_web_swing,
    "web_land": snd_web_land,
    "spider_sense": snd_spider_sense,
    "web_wings": snd_web_wings,
    "web_shield": snd_web_shield,
}


def _encoders():
    out = subprocess.run(["ffmpeg", "-hide_banner", "-encoders"], capture_output=True, text=True).stdout
    return out


def write_ogg(name, data):
    """Writes mono Ogg Vorbis with libvorbis (CI). Falls back to ffmpeg's native encoder, which is stereo only."""
    out = mk(os.path.join(A, "sounds", name + ".ogg"))
    pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
    have_lib = " libvorbis " in _encoders()
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        if have_lib:
            cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-ac", "1", "-q:a", "5", out]
        else:
            cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2",
                   "-ac", "2", "-b:a", "96k", out]
        subprocess.run(cmd, check=True)


# ----------------------------------------------------------------------------- main
def main():
    items = {
        "web_shooter": icon_web_shooter(),
        "spider_suit_helmet": icon_helmet(),
        "spider_suit_chestplate": icon_chest(),
        "spider_suit_leggings": icon_legs(),
        "spider_suit_boots": icon_boots(),
    }
    for name, im in items.items():
        im.save(mk(os.path.join(A, "textures", "item", name + ".png")))
        write_json(os.path.join(A, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        write_json(os.path.join(A, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})

    armor_texture(1).save(mk(os.path.join(A, "textures", "entity", "equipment", "humanoid", "spider_suit.png")))
    armor_texture(2).save(mk(os.path.join(A, "textures", "entity", "equipment", "humanoid_leggings", "spider_suit.png")))
    write_json(os.path.join(A, "equipment", "spider_suit.json"), {
        "layers": {
            "humanoid": [{"texture": f"{NS}:spider_suit"}],
            "humanoid_leggings": [{"texture": f"{NS}:spider_suit"}],
        }
    })
    icon_main().save(mk(os.path.join(A, "icon.png")))

    for name, fn in SOUNDS.items():
        write_ogg(name, fn())
    write_json(os.path.join(A, "sounds.json"),
               {name: {"sounds": [f"{NS}:{name}"], "subtitle": f"subtitles.{NS}.{name}"} for name in SOUNDS})

    # recipes (1.21.11 format)
    write_json(os.path.join(D, "recipe", "web_shooter.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"I": "minecraft:iron_ingot", "S": "minecraft:string", "P": "minecraft:slime_ball", "R": "minecraft:redstone"},
        "pattern": ["ISI", "SPS", "IRI"],
        "result": {"id": f"{NS}:web_shooter", "count": 1}})
    suit = {
        "spider_suit_helmet": ["RRR", "R R"],
        "spider_suit_chestplate": ["R R", "BBB", "RRR"],
        "spider_suit_leggings": ["RRR", "B B", "B B"],
        "spider_suit_boots": ["R R", "B B"],
    }
    for name, pattern in suit.items():
        write_json(os.path.join(D, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment",
            "key": {"R": "minecraft:red_wool", "B": "minecraft:blue_wool"},
            "pattern": pattern,
            "result": {"id": f"{NS}:{name}", "count": 1}})
    write_json(os.path.join(D, "tags", "item", "repairs_spider_suit.json"),
               {"values": ["minecraft:string", "minecraft:red_wool", "minecraft:blue_wool"]})

    # language
    lang = {
        "itemGroup.spiderultimate": "Spider Ultimate",
        "item.spiderultimate.web_shooter": "Web Shooter",
        "item.spiderultimate.spider_suit_helmet": "Spider Suit Mask",
        "item.spiderultimate.spider_suit_chestplate": "Spider Suit Jacket",
        "item.spiderultimate.spider_suit_leggings": "Spider Suit Pants",
        "item.spiderultimate.spider_suit_boots": "Spider Suit Boots",
        "key.category.spiderultimate.main": "Spider Ultimate",
        "key.spiderultimate.shoot": "Shoot Web (current mode)",
        "key.spiderultimate.swing": "Swing (shoot / release)",
        "key.spiderultimate.spider_sense": "Toggle Spider Sense",
        "key.spiderultimate.wings": "Toggle Web Wings",
        "key.spiderultimate.change_mode": "Change Web Mode",
        "key.spiderultimate.special": "Use Special Ability",
        "key.spiderultimate.next_ability": "Next Special Ability",
        "key.spiderultimate.toggle_crawl": "Toggle Wall Crawl",
        "key.spiderultimate.release": "Release Webs",
        "key.spiderultimate.reel": "Reel In Web (hold)",
        "key.spiderultimate.toggle_hud": "Toggle HUD",
        "key.spiderultimate.reload_config": "Reload Config",
        "key.spiderultimate.shoot_left": "Shoot Left Hand",
        "key.spiderultimate.shoot_right": "Shoot Right Hand",
        "spiderultimate.mode.single": "Single Web",
        "spiderultimate.mode.rapid": "Rapid Fire",
        "spiderultimate.mode.zip": "Zip",
        "spiderultimate.mode.pull": "Pull",
        "spiderultimate.mode.trap": "Trap",
        "spiderultimate.mode.bomb": "Web Bomb",
        "spiderultimate.mode.dual": "Dual Web",
        "spiderultimate.ability.swing_boost": "Swing Boost",
        "spiderultimate.ability.quick_zip": "Quick Zip",
        "spiderultimate.ability.air_trick": "Air Trick",
        "spiderultimate.ability.web_shield": "Web Shield",
        "spiderultimate.ability.slow_motion": "Slow Motion",
        "spiderultimate.ability.wall_launch": "Wall Launch",
        "spiderultimate.ability.multi_web": "Multi Web",
        "spiderultimate.hud.mode": "Mode",
        "spiderultimate.hud.ability": "Ability",
        "spiderultimate.hud.shooter": "Shooters",
        "spiderultimate.hud.swinging": "SWING",
        "spiderultimate.hud.zipping": "ZIP",
        "spiderultimate.hud.gliding": "WINGS",
        "spiderultimate.hud.crawling": "CRAWL",
        "spiderultimate.hud.shield": "SHIELD",
        "spiderultimate.hud.sense_on": "Sense ON",
        "spiderultimate.hud.sense_off": "Sense off",
        "spiderultimate.hud.crawl_on": "Crawl ON",
        "spiderultimate.hud.crawl_off": "Crawl off",
        "spiderultimate.msg.no_gear": "Hold the Web Shooter or wear the Spider Suit jacket",
        "spiderultimate.msg.no_energy": "Out of web fluid",
        "spiderultimate.msg.no_midair": "Midair shooting is disabled",
        "spiderultimate.msg.no_target": "No target in range",
        "spiderultimate.msg.cooldown": "Not ready yet",
        "spiderultimate.msg.invalid_target": "The web did not stick",
        "spiderultimate.msg.too_fast": "Too fast for this server",
        "spiderultimate.msg.disabled": "Disabled in the config",
        "spiderultimate.msg.not_now": "Cannot use that right now",
        "spiderultimate.msg.slow_motion_sp": "Slow motion only works in single player",
        "spiderultimate.msg.config_reloaded": "Config reloaded",
        "spiderultimate.msg.crawl_on": "Wall crawl ON",
        "spiderultimate.msg.crawl_off": "Wall crawl off",
        "spiderultimate.msg.wings_on": "Web wings ON",
        "spiderultimate.msg.wings_off": "Web wings off",
    }
    for name in SOUNDS:
        lang[f"subtitles.{NS}.{name}"] = name.replace("_", " ").capitalize()
    write_json(os.path.join(A, "lang", "en_us.json"), lang)
    print("assets generated")


if __name__ == "__main__":
    main()
