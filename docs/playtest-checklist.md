# Playtest checklist: Spellcraft

Run `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1` (checks, GameTests, then the client), or `./gradlew runClient`. Create a Creative world (superflat is easiest) and take the
*Scroll of Upside-Down* from the Tools & Utilities tab. Tick each item; record anything odd
under **Findings** with what you did and what you saw.

Config used below (all in one place): for the mid-roll items set the client config `transitionSeconds = 10` (the sideways point then lasts several seconds; at the default 1 s it is gone in half a second). For the "run out" item set the server config `durationSeconds` low. Restore the defaults afterwards (`transitionSeconds = 1.0`, `durationSeconds = 180`).

## Cast and roll

- [ ] The scroll's inventory/hotbar icon is a parchment with a purple down-arrow (not the plain paper item).
- [ ] Right-click: over ~1 s the view does a smooth half barrel roll; grass ends up across the top.
- [ ] The effect icon (purple down-arrow) and a 3:00 countdown show in the HUD and inventory.
- [ ] Sneak + right-click: the view keeps turning **the same way** back to upright (one full barrel roll overall), not back the way it came. One scroll is used up.
- [ ] Right-click again during that roll-out: the roll reverses smoothly back to flipped, with no jump (needs a second scroll).
- [ ] Sneak + right-click and re-cast only work with the scroll **in your hand** (dispel and reversal are scroll actions). With another item held (or empty hand) while flipped, sneak + right-click does nothing; the spell ends only by milk or expiry. Covered server-side by the `sneak_use_without_scroll_keeps_effect` GameTest. "Sneak" is the vanilla Sneak binding (Left Shift by default); Right Shift does nothing unless you rebind Sneak in Controls.
- [ ] Recast while settled flipped: no animation; the countdown resets to 3:00. One scroll is used up.
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

## Scrolls (every spell)

- [ ] Each use that works makes the scroll vanish in a puff of dark smoke with a short fizzle. Note whether the particle and sound feel right.
- [ ] Scrolls stack to 16 and a stack goes down by exactly one per use, in survival **and** creative.
- [ ] A refused use (Infinity in creative, or while Infinity is active) shows "Scroll not needed; already have effect!" above the hotbar, keeps the scroll, and doesn't swing the arm.
- [ ] Icons: the Scroll of Infinity shows a gold ∞ on the diagonal parchment; the Infinity effect icon is a gold ∞; the Upside-Down effect icon is the purple arrow, re-rendered from its scroll glyph (slightly chunkier than before). Nothing shows the purple-and-black missing texture.
- [ ] Client Mixins: with the mod loaded, the game starts (runClient) with no Mixin errors, and the existing mouse-turning behaviour of Upside-Down still works (MouseHandlerMixin now loads from spellcraft.client.mixins.json; runData does not prove this headlessly).

## Strength of Infinity

Use a **Survival** world. Take the Scroll of Infinity from Tools & Utilities in a creative world, or `/give @s spellcraft:infinity_scroll`.

- [ ] Use it: the HUD shows the Infinity effect with "∞" instead of a countdown.
- [ ] Double-tap Space: you fly. Flight feels like creative flight.
- [ ] `E` opens the **creative** inventory. Items taken from it stay after closing it and after quitting to title and rejoining.
- [ ] Blocks break instantly; placed blocks aren't used up.
- [ ] You still take damage and get hungry (e.g. jump off something while not flying).
- [ ] Drink milk and use a totem: Infinity stays.
- [ ] `/gamemode creative` then `/gamemode survival`: flight and the creative inventory come back (double-tap Space again to fly).
- [ ] `/effect clear @s` while flying high: you fall, and `E` opens the survival inventory.
- [ ] Adventure mode: the scroll works there too.

## Dying with Infinity

- [ ] Wear armor, fill hotbar, main inventory and offhand, then die (e.g. `/kill`). No items appear around you.
- [ ] Respawn: hotbar, main inventory and offhand are back in the same slots; armor is gone; XP is 0; no Infinity, no flight, survival inventory.
- [ ] Die with Infinity, then **quit to title on the death screen**, rejoin and respawn: the items are restored.
- [ ] Die with Infinity in the Nether, respawn in the Overworld: items restored.
- [ ] Item with Curse of Vanishing: gone after an Infinity death.
- [ ] Items in the 2x2 inventory crafting grid and on the cursor when you die with Infinity: note whether they still drop (the stash covers the main inventory, hotbar and offhand only; vanilla handles the crafting grid and cursor).

## Scribing

- [ ] Put paper and lapis in an enchanting table: three options show as usable, with 1/2/3 costs and the usual enchanting-script text.
- [ ] Hover each: the tooltip says "Scribe a random scroll", "25% / 50% / 75% chance of an extreme-tier spell", then the lapis and level cost (red when you're short). If the tooltip is vanilla's plain text instead, the client Mixin did not apply.
- [ ] Click one: the paper turns into a scroll, the lapis and levels are taken, the enchanting sound plays.
- [ ] Hover each option row at its very top and bottom edge: the tooltip appears for the row you are on and not the neighbouring one.
- [ ] A book still enchants normally.
- [ ] With Infinity on (creative inventory), scribing costs nothing.

## Real profile

- [ ] Copy `build/libs/spellcraft-1.0.0.jar` into `%APPDATA%\.minecraft\mods`, launch the NeoForge 26.3 profile, and cast once.

## Findings

(record here)
