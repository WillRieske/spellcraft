# Spellcraft: "Strength of Upside-Down" (design)

**Date:** 2026-09-28
**Status:** Approved in brainstorming; awaiting written-spec review
**Target:** Minecraft Java 26.3, NeoForge 26.3.0.23-beta, Java 25, ModDevGradle

## Summary

Spellcraft is a NeoForge mod that adds spells. The first spell is **Strength of Upside-Down**.

You right-click an *Upside-Down scroll*. Over one second the camera does an eased half barrel roll, until the grass is across the top of your screen and the sky across the bottom. You're still standing on the same grass, because physics doesn't change, but from where you sit you're hanging from a grass ceiling. In creative flight, letting go of the controls "drops" you **up** toward the grass. Your mouse and the A/D keys still feel normal on screen, because the mod quietly compensates for them. Space and Shift keep their real-world meaning, so on screen they look inverted. The HUD and your held item don't move. After three minutes (or earlier, if you sneak + right-click or drink milk), the camera finishes the barrel roll and you're back to normal.

Under the hood, the spell is a vanilla-style **status effect**. The server decides who has it. The client reads that and does everything visual: the camera roll, the input compensation, and the sound flip. Other players see nothing unusual.

## Glossary

| Term | Meaning |
|---|---|
| **Roll** | Rotation of the camera around the direction you're looking. 180° roll = upside-down view. |
| **Status effect** | Vanilla mechanism (like Night Vision): a timed, icon-bearing state on an entity, synced to the client, saved with the player, and cleared by death or milk. |
| **Compensation** | Transforming player input so it feels normal *on screen* despite the rolled view. |
| **Screen-relative / world-relative** | Whether a control's direction is defined by the screen (e.g. "mouse right = view pans right on screen") or by the world (e.g. "Space = away from the ground"). |
| **Mixin** | A small, targeted patch to Minecraft's own code, applied at load time. Used here only where NeoForge offers no event. |
| **GameTest** | Minecraft/NeoForge's built-in automated test framework. It runs tests inside a headless server. |
| **Pure function** | Code with no Minecraft dependencies: same input, same output. Unit-testable with plain JUnit. |
| **MDK** | Mod Development Kit: NeoForge's template project. |

## 1. Scope

**In scope (MVP):**
- A minimal spell framework (a `Spell` record and a generic scroll item), sized only to what Upside-Down needs.
- The Upside-Down spell: status effect, scroll item, and the client-side flip with rolled transitions in and out.
- Unit tests, GameTests, and a manual playtest checklist.
- Dev-environment setup and the repo workflow (§6).

**Out of scope (for now):**
- Other spells, spell registries, mana or any other casting cost.
- Crafting recipes (creative tab only).
- Any change to physics or gravity.
- Showing the caster differently to other players (no upside-down model or particles).
- Publishing to CurseForge or Modrinth.

## 2. Architecture

The mod ID is `spellcraft`. The code has two halves with one interface between them: **"does this player have the `UPSIDE_DOWN` effect?"**

### Common (runs on the server; decides state)

- **`Spell`**: a record `(ResourceLocation id, Holder<MobEffect> effect, int durationTicks, int cooldownTicks)`. There's no registry; a static list holds one entry. Duration and cooldown come from config at cast time.
- **`SpellScrollItem`**: one generic item class, constructed with a `Spell`. It's registered once per spell, starting with `spellcraft:upside_down_scroll`, and listed in the creative tab.
  - *Right-click:* apply the effect for the full duration, whether or not it's already active (recasting resets the timer), then apply the item cooldown if it's greater than 0.
  - *Sneak + right-click while the effect is active:* remove the effect.
  - *Sneak + right-click while inactive:* same as a normal right-click (cast).
- **`ModEffects.UPSIDE_DOWN`**: a `MobEffect` with no tick behavior. It's a marker with an icon and countdown, category *neutral*. It is removable by milk (vanilla default).
- **Server config:** `upsideDown.durationSeconds` (default **180**) and `upsideDown.cooldownSeconds` (default **0**).

### Client (visual and input only; never decides state)

- **`UpsideDownView`**: pure functions with no rendering calls.
  - `rollFor(progress)`: maps transition progress to a roll angle through the easing curve.
  - `ease(t)`: an ease-in-out curve on [0,1]. f(0)=0, f(1)=1, monotonic, no overshoot.
  - `turn(dx, dy, rollDegrees) → (dx', dy')`: rotates the mouse delta by −roll, so mouse movement is screen-relative at any angle. At 0° it's the identity; at 180° it's `(−dx, −dy)`.
  - `strafe(x, rollDegrees)`: `x` if the view is less than 90° from upright, otherwise `−x`.
  - `soundFlipped(rollDegrees)`: `true` if the view is 90° or more from upright.
- **`UpsideDownRollState`**: a client-side state machine, also pure given its inputs.
  - States: `UPRIGHT`, `ROLLING_IN`, `FLIPPED`, `ROLLING_OUT`.
  - Each client tick, it compares "effect present?" with its current state. Effect appears → `ROLLING_IN`; effect disappears → `ROLLING_OUT`.
  - It exposes the current roll angle for a given partial tick, so animation is frame-smooth.
  - **Snap cases** (no animation): recast or refresh while flipped (already `FLIPPED`); first tick after login, respawn, or level change. On those events it resyncs directly to `FLIPPED` or `UPRIGHT`.
  - **Direction:** the roll-in goes 0→180°, and the roll-out continues 180→360° (≡ 0°) in the same direction. The whole spell is one full barrel roll.
  - A cast during `ROLLING_OUT` reverses into `ROLLING_IN` from the current angle, rather than jumping.
- **`ClientEvents`**:
  - `ViewportEvent.ComputeCameraAngles`: `setRoll(getRoll() + currentRoll)`. It adds to the existing roll so other mods' roll is preserved.
  - Movement-input event: `leftImpulse = UpsideDownView.strafe(leftImpulse, currentRoll)`. Forward, jump, and sneak are untouched.
  - Sound listener orientation: set the listener's up-vector from `currentRoll`, so stereo matches the screen. If no event exists, this becomes a second small Mixin.
- **`MouseHandlerMixin`**: wraps the turn deltas passed to the player's look update with `UpsideDownView.turn(...)` whenever `currentRoll ≠ 0`.
- **Client config:** `upsideDown.transitionSeconds` (default **1.0**; `0` = snap).

### Why these boundaries

The spell framework knows nothing about the flip, and the flip knows nothing about scrolls. It only reads the effect. All the math sits in pure classes; event handlers and Mixins are thin plumbing that call into them.

## 3. Behavior

### While active
- **World:** rotated by `currentRoll` (180° once settled).
- **Unchanged:** HUD, crosshair, chat, and your hand and held item. The hand has its own render pass, which the roll doesn't reach. That is correct here, because "you're upside-down too."
- **Third person (F5):** the scene rotates, so you see yourself hanging head-down from the grass.
- **Mouse:** screen-relative at every roll angle.
- **A/D:** screen-relative. The sign switches at 90° during transitions.
- **W/S:** unchanged.
- **Space/Shift in flight:** world-relative. On screen, Space moves you *down* (away from the grass). Gravity and Shift move you *up*.
- **Sound:** stereo matches the screen. It switches at 90°.

### Edge cases
| Situation | Behavior |
|---|---|
| Death | Effect cleared (vanilla); snaps upright on respawn. |
| Dimension change | Effect persists (vanilla); stays flipped (snap on arrival). |
| Logout and login | Effect saved with remaining time; login snaps straight to flipped. |
| Spectator, riding, elytra | Flip applies unchanged; no special code. Elytra feel is checked in the playtest. |
| Milk | Clears the effect, then rolls out. |
| Another mod rolling the camera | Our roll is added to theirs. |

## 4. Error handling

- The client never trusts itself for state. If the effect sync is missing or late, the view simply doesn't flip. No networking code of our own means no desync bugs of our own.
- The Mixin targets one method. If a NeoForge or Minecraft update renames it, the Mixin fails loudly at startup (Mixin `require = 1`) instead of silently doing nothing.
- Config values are range-checked: duration ≥ 1 s, cooldown ≥ 0 s, transition 0–10 s.

## 5. Testing

1. **Unit tests (JUnit, `gradlew test`)**, written first (TDD):
   - `turn` at 0°, 90°, 180°, and 270°: mouse-right always maps to screen-right.
   - `strafe` on each side of the 90° boundary.
   - `ease`: endpoints, monotonicity, no overshoot.
   - `UpsideDownRollState`: every transition, including reversal mid-roll-out, the snap cases, and the 180→360° continuation.
2. **GameTests (`gradlew runGameTestServer`)** for the server rules:
   - Cast grants 180 s.
   - Recast resets to 180 s.
   - Sneak-cast while active removes the effect.
   - Milk clears it.
   - Config values are honored.
3. **Manual playtest checklist** (`docs/playtest-checklist.md`, run via `gradlew runClient`):
   - Roll-in and roll-out: smooth and in the same rotational direction.
   - Mouse and A/D during and after the roll.
   - Creative flight: Space, Shift, and the fall toward the grass.
   - F5 view.
   - HUD and hand unchanged.
   - Sound direction.
   - Elytra, a boat, the Nether, death mid-spell, and logout mid-spell.

## 6. Development environment and workflow

### Tooling
- **JDK 25** (Temurin), installed alongside the existing Java 8. Gradle toolchains pick it per project.
- **Project scaffold:** NeoForge's official ModDevGradle MDK template, cloned with git, with versions pinned exactly in `gradle.properties` to MC 26.3 / NeoForge 26.3.0.23-beta. It's a beta, so the version is pinned, not floating.
- **IntelliJ IDEA 2026.2** + **Minecraft Development** plugin (Mixin and event awareness).
- **Git** from the first commit; **`gh`** CLI for GitHub. The repo is created private when publishing is requested; the license is chosen then (MIT suggested).
- **Claude skills (project-local, committed in `.claude/skills/`):** `minecraft-modding` and `minecraft-testing` from [jahrome907/minecraft-agent-skills](https://github.com/jahrome907/minecraft-agent-skills) (MIT). Only these two of its 13 skills. Read and reviewed before install. Not used: chouzz/minecraft-mod-dev (overlaps; targets 1.21+) and Paper Plugin Dev (wrong platform).
- **Project `CLAUDE.md`:** Gradle commands, Java 25, where the decompiled Minecraft sources live (read real vanilla code; don't guess APIs), and a rule that visual verification belongs to the human.

### Quality gates

These follow `~/.claude/development-methodology.md`.

| Stage | Runs |
|---|---|
| Claude commit gate (`.claude/gate.json`, `gradlew.bat`) | `spotlessCheck` → `build`: Error Prone + `-Xlint:all -Werror`, and JUnit + jqwik + ArchUnit |
| pre-commit | EOF/whitespace, `check-json`, `check-yaml`, `check-merge-conflict`, gitlint (conventional commits), markdownlint, `spotlessCheck` |
| pre-push | `runGameTestServer` |
| CI (GitHub Actions) | All of the above + Gradle wrapper validation |
| On demand | PIT on the pure classes; JaCoCo report |

- **Formatter:** Spotless with palantir-java-format. Add a `.java` entry to `~/.claude/hooks/format_file.py`.
- **ArchUnit rules:**
  1. Nothing outside `spellcraft.client` references `net.minecraft.client..` or our client package. A violation crashes dedicated servers.
  2. `UpsideDownView` and `UpsideDownRollState` reference no `net.minecraft..` at all.
- **jqwik property:** for any roll angle, `turn` maps mouse-right to screen-right.
- **Mixin gap:** client Mixins don't load on the GameTest server. A plan task checks whether the client data-gen run applies them headlessly; if not, the playtest checklist covers them.
- **Not used:** Checkstyle (duplicates the formatter), SpotBugs (overlaps Error Prone), SonarQube (overkill), NullAway (Minecraft code has no null annotations).
- **First cold build** (Minecraft download and decompile) is run by hand before the gate goes live, because of the gate's 300 s per-command limit.

### Setup ownership
| Step | Who |
|---|---|
| Install JDK 25 and `gh` (winget) | Claude, with approval |
| `gh auth login` | **Will** (browser sign-in) |
| Clone MDK and pin versions | Claude |
| Minecraft Development plugin | Claude via IntelliJ CLI (IDE closed) or Will via Settings → Plugins |
| Open the project in IntelliJ | **Will** |
| Review and install skills; write `CLAUDE.md` | Claude |
| Wire the quality gates (Spotless, Error Prone, ArchUnit, jqwik, PIT, pre-commit, `gate.json`, CI) | Claude |
| Create GitHub repo and push | Claude, only when Will asks |

### Loop
- `gradlew test` → `gradlew runGameTestServer` → `gradlew runClient` (playtest) → `gradlew build`. The jar goes into `%APPDATA%\.minecraft\mods` for a final check in the real NeoForge 26.3 profile.
- Claude pipeline: this spec → `writing-plans` → `review-plan` → `executing-plans-will` (TDD for the pure classes) → `verification-before-completion` → `review-will` → `finishing-a-development-branch`. Claude runs tests and builds. Visual checks are handed to Will as "run the playtest checklist" steps.
- Deferred until needed: the GitHub MCP plugin (`gh` suffices), karellen-lsp-mcp (only if API hunting gets slow), and a project `neoforge-conventions` skill (only once real gotchas accumulate).

## 7. Open risks

- **Beta NeoForge API drift:** event names and signatures in 26.3.0.23-beta may differ from the docs, which list 26.1 as the newest. The first plan task verifies each hook (camera angles, movement input, sound listener, the Mixin target) against the decompiled sources before any feature code is written.
- **Sound listener hook** may need a Mixin rather than an event (§2).
- **Frustum culling** with a rolled camera: the roll is in the view matrix, so culling should be correct. The playtest checklist checks for chunks popping at the screen edges.
