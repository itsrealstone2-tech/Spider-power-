# Spider Ultimate (Fabric, Minecraft Java 1.21.11)

A Spider-Man-*inspired* superhero mod for recording cinematic gameplay: **physics-based web swinging** (real rope constraint, momentum, no teleporting), a web shooter with 7 modes, wall and ceiling crawling, spider powers, web wings, and a small HUD. Single player and multiplayer. All art and sounds are original and procedurally generated.

> **Status: written, not yet compiled or played.** The code could not be compiled in the environment it was written in (no access to Minecraft's build files). The physics core is unit-tested; everything that touches Minecraft is untested until the GitHub Actions build and a real in-game run. See *Honest status* below.

## Install (Android / Zalith Launcher)
1. In Zalith, create a profile for **Minecraft 1.21.11** with **Fabric Loader** (0.17 or newer).
2. Put **Fabric API 0.141.6+1.21.11** (or newer for 1.21.11) and `spider-ultimate-1.0.0.jar` in that profile's `mods` folder.
3. Single player: just launch. Multiplayer: the **server and every player** need the mod. If the server lacks it, the mod stays quiet and does not crash anything.
4. Dedicated servers: set `allow-flight=true` in `server.properties`, otherwise long swings and glides can get you kicked for "flying".

## Getting the items
Creative tab **Spider Ultimate**: Web Shooter and Spider Suit (mask, jacket, pants, boots). Survival recipes are included. Abilities need the shooter in your hand or the suit jacket worn (`requireGear` in the config can turn that off).

## Controls (all rebindable under Options > Controls > Spider Ultimate)
| Key | Action |
|---|---|
| **R** | Shoot web in the current mode |
| **F** | Swing: shoot a rope (press again to release, or hold, see `swingToggle`) |
| **Space** (while swinging) | Jump off the rope with a boost |
| **Z** | Release webs |
| **J** (hold) | Reel the rope in (shorter rope = faster swing) |
| **C** | Change web mode |
| **V** / **B** | Use / cycle special ability |
| **G** | Toggle spider sense |
| **X** | Toggle web wings |
| **Left Alt** | Toggle wall crawl mode |
| **H** | Toggle HUD, **K** reload config |
| Unbound | Shoot Left Hand, Shoot Right Hand |

**On a phone:** open the Zalith control editor and add on-screen buttons mapped to these keys. Suggested minimum: F, R, Z, C, V, G, X, Left Alt plus the usual jump button.

Web modes: Single, Rapid, Zip, Pull, Trap, Bomb, Dual. Abilities: Swing Boost, Quick Zip, Air Trick, Web Shield, Slow Motion (single player only), Wall Launch, Multi Web.

## Config
`config/spiderultimate.json` is created on first start (range, cooldowns, energy costs, swing strength, speed limits, crawl speed, effects and more). Press **K** in game to reload it.

## Building
Push to GitHub: the workflow in `.github/workflows/build.yml` generates the textures and sounds (`tools/generate_assets.py`), builds with JDK 25 / Gradle 9.8.2, and publishes the jar as the **latest** Release. If the full build fails, it retries without the optional client mixins (crawl body orientation and limb poses) so you still get a working jar. The release notes say which build you got.

Locally: `python3 tools/generate_assets.py` then `gradle build` (needs numpy, scipy, Pillow, ffmpeg).

## Honest status
**Tested:** the pure-Java physics (rope swing, zip, glide, crawl math) via `tools/test/PhysicsSelfTest.java`: energy, no-teleport step limits, tangential speed gain, crawl-surface handedness. Source parses, JSON assets validate, and lang/sound/item names cross-check.

**Not tested (needs a real build and play-through):**
- Compilation against Minecraft 1.21.11 (names were checked against docs, but not by a compiler).
- Feel of the swing in game, rope rendering, web projectiles, wings, shield visuals.
- Wall/ceiling crawl transitions and the body rotation / limb poses (mixins; optional).
- Multiplayer validation under real latency, slow motion, spider sense glow.
- Generated textures and sounds in game (the Spider Suit look in particular).
