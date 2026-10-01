# Playtest checklist: Strength of Upside-Down

Run `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1` (checks, GameTests, then the client), or `./gradlew runClient`. Create a Creative world (superflat is easiest) and take the
*Scroll of Upside-Down* from the Tools & Utilities tab. Tick each item; record anything odd
under **Findings** with what you did and what you saw.

Config used below (all in one place): for the mid-roll items set the client config `transitionSeconds = 10` (the sideways point then lasts several seconds; at the default 1 s it is gone in half a second). For the "run out" item set the server config `durationSeconds` low. Restore the defaults afterwards (`transitionSeconds = 1.0`, `durationSeconds = 180`).

## Cast and roll

- [ ] The scroll's inventory/hotbar icon is a parchment with a purple down-arrow (not the plain paper item).
- [ ] Right-click: over ~1 s the view does a smooth half barrel roll; grass ends up across the top.
- [ ] The effect icon (purple down-arrow) and a 3:00 countdown show in the HUD and inventory.
- [ ] Sneak + right-click: the view keeps turning **the same way** back to upright (one full barrel roll overall), not back the way it came.
- [ ] Right-click again during that roll-out: the roll reverses smoothly back to flipped, with no jump.
- [ ] Sneak + right-click and re-cast only work with the scroll **in your hand** (dispel and reversal are scroll actions). With another item held (or empty hand) while flipped, sneak + right-click does nothing; the spell ends only by milk or expiry. Covered server-side by the `sneak_use_without_scroll_keeps_effect` GameTest. "Sneak" is the vanilla Sneak binding (Left Shift by default); Right Shift does nothing unless you rebind Sneak in Controls.
- [ ] Recast while settled flipped: no animation; the countdown resets to 3:00.
- [ ] Drink milk: the effect clears, then the view rolls out.
- [ ] Let the effect run out (set `durationSeconds` low in the world's `serverconfig/spellcraft-server.toml` to save time): the view rolls out at the end.
- [ ] Client config `transitionSeconds = 0`: flips snap instantly both ways.

## Controls while flipped

- [ ] Mouse right/left/up/down pans the view right/left/up/down **on screen**.
- [ ] Mid-roll (around the sideways point), the mouse is still screen-relative. If it is mirrored only mid-roll, report "turn() sin sign". Do not just flip the sign in `turn` and its property test together: the property test shares the same axis assumption and would hide the error. Re-derive the axes from `Camera.setRotation` (docs/api-notes.md fact 1) first.
- [ ] A/D move you left/right on screen; W/S unchanged.
- [ ] During a roll, A/D swap exactly once (at the sideways point).
- [ ] Creative flight: Space moves you **down** on screen (away from the grass), Shift moves you up; stopping flight, you fall **up** on screen toward the grass.

## View

- [ ] HUD, crosshair, chat and hotbar stay upright and unmoved.
- [ ] Your hand and held item are unchanged (not flipped).
- [ ] F5 behind and in front: the scene is rotated; you hang head-down from the grass. (Front view negates the roll in vanilla, so mouse and A/D compensation is exact only at 0° and 180° there; mid-roll front view is an accepted limitation. Note anything odd.)
- [ ] Look nearly straight up and straight down (pitch near ±90°) while flipped: the view does not glitch or snap.
- [ ] Mods > Spellcraft > Config: the server and client config screens open and show readable labels.
- [ ] Turning quickly: no chunks popping in or out at the screen edges.

## Sound

- [ ] Flipped: a sound on your screen-left (e.g. a note block) plays in your left ear.
- [ ] During the roll, stereo pans smoothly with the view.

## Situations

- [ ] Elytra while flipped: controllable. Note how it feels.
- [ ] Boat while flipped: view flipped, controls usable.
- [ ] Standard minecart: flip applies. (With the experimental new-minecart feature enabled the camera takes a different code path: the flip is not drawn and mouse/A/D stay normal, because the compensation follows what is drawn; known, see `docs/api-notes.md` fact 2.)
- [ ] Nether portal mid-spell: you arrive still flipped, with no roll animation.
- [ ] Die mid-spell: you respawn upright, with no animation.
- [ ] Quit to title mid-spell and reload the world: flipped immediately, countdown continued.
- [ ] Optional (Open to LAN + a second client): the other player sees nothing unusual — no particles, normal model.

## Real profile

- [ ] Copy `build/libs/spellcraft-1.0.0.jar` into `%APPDATA%\.minecraft\mods`, launch the NeoForge 26.3 profile, and cast once.

## Findings

(record here)
