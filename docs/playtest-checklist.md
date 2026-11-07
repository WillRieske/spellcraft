# Playtest checklist: Spellcraft

Run `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1` (checks, GameTests, then the client), or `./gradlew runClient`. Create a Creative world (superflat is easiest) and take the
*Scroll of Upside-Down* from the Tools & Utilities tab. Tick each item; record anything odd
under **Findings** with what you did and what you saw.

Config used below (all in one place): for the mid-roll items set the client config `transitionSeconds = 10` (the sideways point then lasts several seconds; at the default 1 s it is gone in half a second). For the "run out" item set the server config `durationSeconds` low. Restore the defaults afterwards (`transitionSeconds = 1.0`, `durationSeconds = 180`). The Trailing section names its own server settings (`trailing.durationSeconds`, default 480; `trailing.maxTrailBlocks`, default 1024) where it uses them.

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

## Strength of Trailing

Use a **Survival** world with open ground. `/give @s spellcraft:trailing_scroll`, or take it from Tools & Utilities in creative.

- [ ] Scroll icon: a light-blue flame on the parchment. Effect icon: the same flame; the HUD shows an 8:00 countdown.
- [ ] Walk: a light-blue flame appears on each block you step **off**, never under you. Sprint: same, a longer line.
- [ ] Jump: the block you jumped from gets a flame; nothing appears in the air; the block you land on gets one when you leave it.
- [ ] Stand still: the far end of the trail goes out about one block a second, toward you.
- [ ] Walk over soul sand or soul soil: those flames linger about twice as long before going out.
- [ ] Walk over a snow layer: the flame replaces the snow. Walk over carpet or along the top of slabs: no flames there (accepted).
- [ ] Walk back through your own trail: no damage, no burning.
- [ ] A zombie, cow or villager in the trail catches fire and takes damage. A dropped item burns up.
- [ ] Tame a wolf, then lure it into the trail: it is unharmed. (Optional, LAN: a teammate via `/team` is unharmed; a player on no team burns.)
- [ ] Walk through an obsidian portal frame (4×5) in the Overworld: no portal lights.
- [ ] Walk past TNT and along wooden planks: nothing catches; the flames never spread.
- [ ] Throw a splash water bottle at a flame: it goes out. Break a flame by hand: the extinguish sound plays.
- [ ] Ride a horse, boat or minecart: no flames.
- [ ] Creative flight: no flames; landing and walking: flames again. `/gamemode spectator` and move along the ground: no flames.
- [ ] Walk through a nether portal with the effect on: no flame appears on arrival, and none on the portal block itself; the first ordinary block you step off in the new dimension lights; the same coming back.
- [ ] Sneak + right-click with a second scroll: the effect ends; the trail keeps fading on its own.
- [ ] Drink milk: the effect ends the same way.
- [ ] Recast while the effect is on (right-click a second scroll without sneaking): the HUD countdown resets to 8:00 and the second scroll is used up; the existing trail keeps fading.
- [ ] Scribe paper with one lapis a few times: a Scroll of Trailing comes up among the results.
- [ ] Server config `trailing.maxTrailBlocks = 5`, then walk a line: only the last five flames stand at any time. Restore 1024.
- [ ] Server config `trailing.durationSeconds = 20`: the effect ends after 20 s and the trail keeps fading afterwards. Restore 480.
- [ ] Quit to title mid-trail and rejoin: the trail is still there and keeps fading; walking back through it is still safe.
- [ ] `/setblock ~ ~ ~ spellcraft:trail_fire`: it looks like soul fire and burns you (no owner); it disappears by itself on a random tick (about a minute on average at the default `randomTickSpeed`, sometimes much longer; `/gamerule randomTickSpeed 100` makes it seconds).
- [ ] Mods > Spellcraft > Config: the `trailing` section shows "Duration (seconds)" and "Most flames per trail".

## Strength of Evoking

Use a **Survival** world with open flat ground and a few animals (cows, pigs) about. `/give @s spellcraft:evoking_scroll`, or take it from Tools & Utilities in creative. F and V are the defaults; Controls → Spellcraft shows "Evoking: Fangs" and "Evoking: Summon Vexes". F and V both show red in Controls. F shares its key with Swap Item With Offhand, and V with the F3-chord Dump Version Info. Both conflicts are accepted, and pressing the key still reaches the mod's mapping.

- [ ] The dev client reaches the title screen and a world loads with the mod (the enum extension, key mappings and particle providers link; a malformed extension fails at launch with a loading error; a mis-wired proxy fails at the first casting player's render, covered by the F items).
- [ ] Scroll icon: a light-mint "Y" with raised arms, a one-pixel neck and a square head, on the parchment. Effect icon: the same glyph; the HUD shows "∞".
- [ ] Recast while the effect is on: "Scroll not needed; already have effect!", the scroll is kept. Sneak + right-click with a scroll: the effect ends. Drink milk: the effect ends.
- [ ] F with a cow a few blocks ahead: the evoker's "prepare attack" sound; in F5, your arms go up like an evoker's and sway; dark purple particles swirl at both hands for two seconds. A second later fangs ripple outward across a 13×13 square in front of you, one row per tick, none under your own feet; the cow is bitten (6 damage).
- [ ] Turn slowly on the spot and press F each time it is ready (every five seconds): the square turns with you, your block sliding block by block along its edge; facing a corner direction you stand on the corner.
- [ ] F with nothing ahead: no arms, no sound; after a second "No targets in range" above the hotbar. Press F twice quickly with nothing ahead: one message.
- [ ] F while the arms are still up, or within five seconds of a cast: nothing happens (no message; a known decision, say if you want a hint).
- [ ] Stand on a hill above a cow, and in a pit below one: the fangs find the ground at both heights.
- [ ] V: the "prepare summon" sound, arms up for five seconds, pale particles at the hands; after a second three vexes appear 2–3 blocks from you, one block up. They fly around you and ignore you. Tame a wolf: they ignore it. Lead a cow or pig within five blocks of a vex: it charges it. A cow eight blocks away is ignored. The vexes die on their own after 30–120 s.
- [ ] With vexes out, press F at a cow near them: the fangs do not hurt your vexes. Cast Strength of Trailing and walk your vexes through the trail: unharmed.
- [ ] Creative players and spectators ahead are not targets (a LAN second client in creative is never bitten).
- [ ] Press F with Evoking on: your hands do **not** swap. Sneak + scroll to end the effect, then F: hands swap as before. Rebind Fangs to another key in Controls: F swaps again even with Evoking on.
- [ ] Press V, then open the inventory within the five seconds: no "Casting Fangs" / "Casting Vexes" row in the effect list; the HUD shows no icon for them either; the doll in the inventory has its arms raised.
- [ ] First person during a cast: your own hand particles do not block the view (they may show at the screen edge), and your arms stay vanilla.
- [ ] No vanilla swirl around the body during a cast, and no extra mint swirl besides the two hand particles.
- [ ] Type "f" and "v" in chat, and in an anvil rename box: nothing casts (the keys work only in game).
- [ ] Press Esc (singleplayer) during a V cast and wait ten seconds: nothing piles up; on resume the hand particles continue normally.
- [ ] Optional (Open to LAN + a second client): the other player sees your arms raised and the hand particles during a cast, and nothing else unusual.
- [ ] Optional (dedicated server with the built jar, a client joining): the server starts without a client-class error, the client joins, and a cast works.
- [ ] In Creative, stand next to a nether portal with a cow within six blocks ahead, press F (then, after it ends, V), and step in: no fangs appear anywhere in the Nether; for V, the vexes appear beside you in the Nether (the portal is instant in Creative).
- [ ] Mods > Spellcraft > Config: unchanged (Evoking has no settings).

## Real profile

- [ ] Will installs the new jar. From the repo root, in a normal PowerShell window (the Claude app's shells cannot write under `%APPDATA%`), he runs the command below. It builds, then copies the jar whose name carries `mod_version` into `%APPDATA%\.minecraft\mods` and removes every other `spellcraft-*.jar` there; the old `1.0.0` jar goes away and the `1.2.0` jar is replaced. He then launches the NeoForge 26.3 profile and casts each scroll once.

  ```powershell
  $ver = (Select-String '^mod_version=(.+)$' gradle.properties).Matches[0].Groups[1].Value
  $jar = "build\libs\spellcraft-$ver.jar"
  & .\gradlew.bat build; if ($LASTEXITCODE -ne 0) { throw "build failed" }
  if (-not (Test-Path $jar)) { throw "Build first: $jar is missing" }
  Copy-Item $jar "$env:APPDATA\.minecraft\mods\"
  Get-ChildItem "$env:APPDATA\.minecraft\mods\spellcraft-*.jar" | Where-Object Name -ne "spellcraft-$ver.jar" | Remove-Item
  ```

## Findings

(record here)
