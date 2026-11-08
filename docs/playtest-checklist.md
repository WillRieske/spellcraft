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
- [ ] A zombie, cow or pig in the trail catches fire and takes damage. A villager, a wandering trader or an iron golem does not. A dropped item burns up.
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

Use a **Survival** world with open flat ground and a few animals (cows, pigs) about. `/give @s spellcraft:evoking_scroll`, or take it from Tools & Utilities in creative. Y and V are the defaults; Controls → Spellcraft shows "Evoking: Fangs" and "Evoking: Summon Vexes". Y shows no conflict; V shows red (it shares the F3-chord Dump Version Info, accepted).

- [ ] Launch the client and cast: this proves what `runData` cannot, the real render of the raised arms.
- [ ] Scroll icon: a light-mint "Y" with raised arms, a one-pixel neck and a square head, on the parchment. Effect icon: the same glyph; the HUD shows "∞".
- [ ] Recast while the effect is on: "Scroll not needed; already have effect!", the scroll is kept. Sneak + right-click with a scroll: the effect ends. Drink milk: the effect ends.
- [ ] Y with a cow a few blocks ahead: the evoker's "prepare attack" sound; in F5, your arms go up like an evoker's and sway; dark purple particles swirl at both hands for two seconds. A second later one fang rises under the cow (none anywhere else) and bites it (6 damage).
- [ ] Turn slowly on the spot and press Y each time it is ready (every five seconds): each time, the fangs find a cow ahead of you, wherever the square points.
- [ ] Y with nothing ahead: no arms, no sound; after a second "No targets in range" above the hotbar. Press Y twice quickly with nothing ahead: one message.
- [ ] Y while the arms are still up, or within five seconds of a cast: nothing happens (no message; a known decision, say if you want a hint).
- [ ] Stand on a hill above a cow, and in a pit below one: the fangs find the ground at both heights.
- [ ] V: the "prepare summon" sound, arms up for five seconds, pale particles at the hands; after a second three Bound Vexes appear 2–3 blocks from you, one block up. They look like vexes, fly around you and ignore you. Tame a wolf: they ignore it. They do not die on their own (watch one for three minutes).
- [ ] With vexes out, press Y at a cow near them: the fangs do not hurt your vexes. Cast Strength of Trailing and walk your vexes through the trail: unharmed.
- [ ] Creative players and spectators ahead are not targets (a LAN second client in creative is never bitten).
- [ ] Press F with Evoking on: your hands swap as normal. Press Y: the cast starts and nothing swaps (Y is free in vanilla). Rebind Fangs to F in Controls: F casts instead of swapping while Evoking is on, and swaps again once the effect ends. Rebind Fangs to Shift+F: a plain F press swaps, Shift+F casts.
- [ ] Press V, then open the inventory within the five seconds: no "Casting Fangs" / "Casting Vexes" row in the effect list; the HUD shows no icon for them either; the doll in the inventory has its arms raised.
- [ ] First person during a cast: your own hand particles do not block the view (they may show at the screen edge), and your arms stay vanilla.
- [ ] No vanilla swirl around the body during a cast, and no extra mint swirl besides the two hand particles.
- [ ] Type "y" and "v" in chat, and in an anvil rename box: nothing casts (the keys work only in game).
- [ ] `/tick freeze`, press V, wait a few seconds, `/tick unfreeze`: no burst of hand particles.
- [ ] Fangs near the edge of simulation distance place no fangs in the far, non-ticking cells.
- [ ] On Peaceful, press V: the action bar says `Vexes cannot be summoned on Peaceful`, arms stay down, no cooldown: switch to Normal and V casts at once.
- [ ] With `/gamerule pvp false`, Y with only another player in the square gives `No targets in range`, and V's vexes never attack another player or that player's vexes. With pvp on, they attack another player (or that player's vexes) only after it hits you, your pets or your vexes, or after you hit it.
- [ ] Press Esc (singleplayer) during a V cast and wait ten seconds: nothing piles up; on resume the hand particles continue normally.
- [ ] Optional (Open to LAN + a second client): the other player sees your arms raised and the hand particles during a cast, and nothing else unusual.
- [ ] Optional (dedicated server with the built jar, a client joining): the server starts without a client-class error, the client joins, and a cast works.
- [ ] In Creative, stand next to a nether portal with a cow within six blocks ahead, press Y (then, after it ends, V), and step in: no fangs appear anywhere in the Nether; for V, the vexes appear beside you in the Nether (the portal is instant in Creative).
- [ ] Mods > Spellcraft > Config: unchanged (Evoking has no settings).

## Evoking II (Part 1)

Survival world, open ground, a village nearby if you can. The items below add to the Strength of Evoking ones above. Where a golem can hurt you, give yourself Resistance (`/effect give @s resistance 600 4`). (Creative players cannot make a golem mad.)

- [ ] Use the same game profile where you had Fangs on F (its saved settings carry over): Controls shows Fangs on Y, not F.
- [ ] Y at a group of three cows standing apart: three fangs, one under each, none on the grass between them.
- [ ] Y at a ravager (`/summon ravager` is fine) or at an iron golem that is mad at you (hit a village golem once first, then step back): several fangs rise under it, one on each block it stands on.
- [ ] Stand on a cliff 10–14 blocks above a cow and press Y: the fang reaches it. A cow on a ledge 6 blocks above you: bitten. A cow 7 or more blocks above you, or 15 or more below: "No targets in range".
- [ ] Hit a pig so it runs, then press Y at it while it flees: a fang rises where it was, and a second one where it ran to; no third.
- [ ] Y with only a villager, a wandering trader, or a calm iron golem ahead: "No targets in range". Vexes ignore them too.
- [ ] Hit a village iron golem with a sword again and again: when its health reads 25.0 (use the `/data get entity @e[type=iron_golem,limit=1,sort=nearest] Health` command, which shows `25.0f`) it stops losing health, and on that hit it stops chasing you. Your tamed wolf (have one with you) and your fangs stop there too.
- [ ] Make a golem mad at you, then call your vexes next to it: once it hits you (or you hit it), the vexes fight it; it ends at 25 health and calms down.
- [ ] With Strength of Trailing on too, make a mad golem chase you across your fire trail: the golem is not hurt and does not catch fire.
- [ ] Summon vexes near a village golem: the golem never swings at them.

## Evoking II (Part 2)

Survival world, open ground, a village nearby if you can, with Strength of Evoking on. The vexes in the Strength of Evoking items above are Bound Vexes now; these items add to them. Where a golem or a creeper can hurt you, give yourself Resistance (`/effect give @s resistance 600 4`).

- [ ] Press V four times (wait for each cooldown): 3, 6, 9, 12 vexes (`/execute if entity @e[type=spellcraft:bound_vex]` prints the count). A fifth press: "You have too many vexes" above the hotbar, arms stay down, no sound; `/kill @e[type=spellcraft:bound_vex,limit=1]` and V works again at once (no cooldown was spent on the refusal). `/kill @e[type=spellcraft:bound_vex,limit=2]` from twelve: V summons two. From eleven: one.
- [ ] Walk and sprint around: the vexes keep up and hover 2–4 blocks from you, and settle there without darting through you. `/tp @s ~15 ~ ~`: they pop up beside you within a second.
- [ ] Right-click a vex with an empty hand (not sneaking): the vex's sound and your arm swings; it stops following and drifts around that spot. Walk 20 blocks away: it stays. Right-click it again: it follows (it may finish a short drift first). Right-click while holding something, or while sneaking: nothing. A friend's click (LAN): nothing.
- [ ] Hold an ender pearl (or a shield) in your off hand and an empty main hand, then right-click your vex: stay toggles, and no pearl is thrown (no shield is raised).
- [ ] Walk through a nether portal with vexes near you: they arrive beside you in the Nether, still yours (a click still toggles stay). A staying vex stays behind in the Overworld, and so does a following vex more than 16 blocks from you when you step in.
- [ ] Log out and back in: the vexes are where you left them, still yours. Quit to title and reopen the world: same.
- [ ] Die (or, on LAN, have the friend whose vexes are out log out): the vexes hover where they are and fight nothing until you respawn near them (or the friend returns).
- [ ] A zombie attacks you: the vexes go for it. Hit a cow yourself: they go for the cow. A creeper walks up to you: ignored until it hurts you or a vex, then they attack it. A zombie attacks your wolf or a village golem: the vexes go for it.
- [ ] Your tamed wolf (standing, no target) joins when a vex picks a target; a sitting wolf stays put. A creeper that hurt a vex: the vexes attack it, your wolf does not.
- [ ] In the Nether, near zombified piglins: the vexes attack them (the game counts them as hostile mobs), which angers the whole group. Note how that plays; it is the spec's rule, not a bug.
- [ ] `/summon creeper ~5 ~ ~ {NoAI:1b,ignited:1b,Fuse:200s}` with your vexes near you: they fly straight away from it to about eight blocks and hold there; when it explodes ten seconds later they come back to you. Then let a normal creeper walk up and start hissing: the vexes back off; run more than seven blocks away so it stops hissing: they come back.
- [ ] Make a village golem mad at you and stand so a vex hovers between you and the golem: when the golem swings at you, the vex darts sideways out of the swing. The vexes fighting it stop at 25 health and it calms down (Part 1).
- [ ] Hurt a calm golem without angering it, standing a few blocks from it with your vexes (`/damage @e[type=iron_golem,limit=1,sort=nearest] 40`): one vex flies to its chest, an iron ingot appears in its hand there, and every 1.5 seconds you hear the repair sound (an anvil-like clank; the vex's arm may not move, the vex model has no swing) until the golem is full; then it holds its sword again. A second vex nearby does not join the repair. Walk off mid-repair: the vex finishes (at most about three seconds) before it follows.
- [ ] `/summon spellcraft:bound_vex`: a vex that hovers and fights nothing, even with a zombie next to it; its name reads "Bound Vex" (`/data get entity @e[type=spellcraft:bound_vex,limit=1,sort=nearest] id` shows `spellcraft:bound_vex`; `/kill` it and the death message names it).
- [ ] On Peaceful: existing Bound Vexes vanish (vanilla's monster rule); V says "Vexes cannot be summoned on Peaceful". Back on Normal, V works.
- [ ] Optional (LAN, two players with vexes, `/gamerule pvp false`): your vexes and theirs leave each other alone; with pvp on, they fight only after one side hits the other.
- [ ] Known until Part 3: a vex that is unloaded (you leave the area), saved and reloaded, or carried through a portal while repairing a golem keeps the ingot in its hand instead of its sword.
- [ ] Known and accepted: two vexes can land on the same spot after a portal; a vex only follows or rides a portal while its chunk is loaded, so after an elytra flight, a pearl or a `/tp` far away, vexes left in unloaded chunks stay there until you come back.
- [ ] If this world still has round-one vexes (summoned before this update): those vanilla vexes may attack you for their remaining seconds, then die on their own.

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
