# Spellcraft

A NeoForge mod for Minecraft that adds spells cast from scrolls. It has six:

- <img src="src/main/resources/assets/spellcraft/textures/item/upside_down_scroll.png" alt="" width="24" height="24"> **Strength of Upside-Down**: for three minutes (configurable) your view rolls upside down while
  physics stays the same.
- <img src="src/main/resources/assets/spellcraft/textures/item/infinity_scroll.png" alt="" width="24" height="24"> **Strength of Infinity**: survival and adventure players get creative flight, the creative
  inventory and instant building, but still take damage, get hungry and can die. It has no time
  limit; only death or `/effect clear` removes it. If you die with Infinity, you keep your hotbar,
  main inventory and offhand through respawn (armor and XP are lost).
- <img src="src/main/resources/assets/spellcraft/textures/item/trailing_scroll.png" alt="" width="24" height="24"> **Strength of Trailing**: for eight minutes (configurable) you leave soul fire on most solid blocks
  you step off. It never hurts you, spares your allies (your team, your pets, your own vexes, villagers, wandering traders) while you are in the
  same dimension, never burns an iron golem, and burns everything else; it spreads to nothing and lights no TNT. The trail is a fuse: the oldest flame goes out once a
  second (every two seconds over soul sand or soul soil), so it burns back toward you when you stop.
- <img src="src/main/resources/assets/spellcraft/textures/item/evoking_scroll.png" alt="" width="24" height="24"> **Strength of Evoking**: an evoker's powers, with no time limit. Press **Y** to raise your arms
  and, a second later, make evoker fangs rise under every enemy in the 13×13 square ahead of you (a
  big mob that covers several blocks gets one fang under each block; enemies up to 14 blocks below
  you and 6 above count). An enemy that steps off its block before the bite gets one more fang
  under its new spot. Press **V** to call three **Bound Vexes** to your side. They stay until they
  die (Peaceful removes them), and you can have twelve at once: with ten or eleven, V calls just
  enough to make twelve, and at twelve V only says "You have too many vexes". They fly around you,
  pop up beside you when you get more than 12 blocks away, and follow you through portals when they
  are within 16 blocks of you and not told to stay. They fight whatever attacks you, your pets or
  them, whatever you hit, whatever your pets, villagers and golems are fighting or attacked by, and
  any hostile mob that comes within four blocks of them in each direction (a box 8 wide and 7 tall;
  not creepers, unless one attacks first); your tamed wolves join their fights.
  Sneak and right-click a vex with an empty hand to make it stay: it hovers on that spot and guards a
  13 × 13 block area around it; do it again and it follows you. Right-click a vex while holding a
  helmet, chestplate, leggings, a weapon, a shield, a bow or crossbow, a splash or lingering potion or
  an arrow and it takes one and hands back what it had there. You can see the armor on it. A vex with a
  bow, a harmful potion or arrows shoots from a distance, and holds its fire when you or an ally is in
  the way; a vex with a healing potion (splash or lingering) throws it at you and your allies when you
  are badly hurt; a shield blocks hits from the front. Nothing a vex holds is ever used up.
  Right-click with an empty hand (not sneaking) to take its weapon back; everything you gave drops
  when it dies. A vex flies away from a hissing creeper, sidesteps after an iron golem's swing, and
  patches up a hurt iron golem with an iron ingot. Your vexes never fight a zombie villager: one flies
  up, feeds it a golden apple and throws it a potion, and twenty seconds later it is a villager again,
  and the cured villager gives you the cure discount. It costs you nothing, and your fangs and village
  golems leave the zombie villager alone while it is cured. When an enemy vex attacks you or your
  allies, one of your vexes holds out an emerald to it first: two times in three it takes it and flies
  off for good, and otherwise the fight goes on as usual. At night, in the Nether and in the End
  your vexes close in and hover just over your head (about three blocks up, and they stop dead when
  they arrive), and they look around more often. One of them pulls you out of lava toward the nearest
  shore (in the Nether it then douses you with a bucket of water), pushes you up when you are
  drowning under open water, and if you fall off the End's island it catches you, puts you back on
  solid ground and feeds you an enchanted golden apple. A ghast's fireball that is about to hit you
  is batted back at the ghast, and the fireball is yours, so it can kill the ghast in one hit. In the
  End they break the end crystals while the dragon lives (the blast does not hurt or throw you, your
  pets or your vexes), and every melee hit they land there does three times the damage.
  Fangs spare you and your allies (your team, your pets, your own vexes,
  villagers, wandering traders, and iron golems that are not mad at you) and hurt everything else;
  vexes spare them too and fight only what the list above says. You, your pets, your fangs and your
  vexes cannot take an iron golem below 25 health; when it gets that low, it calms down and stops
  being mad at you. The keys rebind in Controls. Sneak + scroll or milk turns it off.

  - **Team moves.** Your vexes act as a team. When something big comes close they grab conjured shields and take
    turns bashing it back (**Shield**); when a strong monster goes after one of them, that vex and two friends pull
    out crossbows that fire three arrows at once (**Multishot**); when you or an ally of yours drops below half
    health, up to three vexes circle the hurt one, shove away anything that comes close and knock arrows back
    (**Evader**); and when a giant (a Ravager, a Warden, the Wither, the Dragon) shows up and all twelve vexes are
    out, they gear up in netherite and diamond, line up in a 3 × 4 block and charge together while the crossbow
    vexes fire rockets (**Raid**). The conjured gear vanishes when the move ends and your own gear comes back; a vex
    in a move answers a click with "That vex is busy". Set `teamMoves` in the server config (section `evoking`) to
    `MANUAL` to turn the automatic triggers off; then **J** picks a move and **K** starts it. Both keys rebind in
    Controls, where they sit with the Evoking keys.

  - **Casters.** One Bound Vex in three is a Caster: it wears a torn purple robe, never fights, keeps four to six
    blocks from its target, and casts Mirror or Ghostrail on you (Ghostrail first when you are in trouble). In a Raid
    it joins only to cast Mirror and hold its place.
  - **Scroll mobs.** Evokers one time in four, and vindicators and pillagers one in ten, spawn with a Mirror or
    Ghostrail scroll in the offhand and use it on themselves when they get a target. Kill one with a player's attack
    and it may drop the scroll (Looting helps).
  - **Villager ghosts.** A villager a monster kills leaves a ghost. When you have Strength of Evoking and are
    hurt below half health or have three monsters close, with a monster near, the nearest villager death within
    48 blocks of you sends its ghost (one whose spells are ready, if any is): it floats to you, casts the spells
    on you and fades when the fight is over. Only one ghost helps at a time. A villager killed by lava, a fall or your own side leaves none.

- <img src="src/main/resources/assets/spellcraft/textures/item/mirror_scroll.png" alt="" width="24" height="24"> **Strength of Mirror**: for six minutes (not configurable) thirteen copies of you stand around you
  and wander near you. A monster that picks you as its target almost always (13 times in 14) picks a copy instead; any mob is
  fooled, a golem that goes for an evoker with copies included, but an iron golem never goes for a player's copies. The copies are
  your allies, have 10 health, drop nothing and give no experience, and end with the effect (the last one dying ends it too). A
  scroll cast while it is active is refused; sneak and use the scroll to dispel it.
- <img src="src/main/resources/assets/spellcraft/textures/item/ghostrail_scroll.png" alt="" width="24" height="24"> **Strength of Ghostrail**: for four minutes (not configurable) you fly through blocks, unhurt (only `/kill` and the
  void still hurt), leaving a trail of white smoke. When it ends inside blocks you are moved to the nearest free spot within eight blocks
  (the spot you started at, if there is none), with no fall damage. Creative players cannot use the scroll; sneak and use the scroll to end it.

Each scroll is single-use. Scribe one by putting paper and lapis in an enchanting table: you get
a random scroll, and more lapis raises the odds of an extreme-tier spell. Creative players can
take any scroll from the Tools & Utilities tab, or use `/give @s spellcraft:<spell>_scroll`
(`upside_down`, `infinity`, `trailing`, `evoking`, `mirror` or `ghostrail`).

Minecraft 26.3, NeoForge 26.3.0.23-beta, Java 25. Windows is the development platform.

## Run it

Build, test and launch the dev client in one step (from the repo root, in PowerShell or cmd):

```bash
powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1
```

It runs the commit-gate checks, then the server GameTests, then launches the client, or leaves
it running if it already has the current build. It asks `y/N` before stopping any running
instance. `-CheckOnly` prints the build stamp and the running instances, without changing
anything. Exit codes are listed in the script header.

## Other commands

| Command | What |
|---|---|
| `./gradlew build` | Format check, compile, unit tests, jar |
| `./gradlew test` | Unit tests only |
| `./gradlew runGameTestServer` | GameTests on a headless server |
| `./gradlew runGameTestServer "-PgameTests=spellcraft:trail_*"` | Only the GameTests whose ID matches (wildcards allowed) |
| `./gradlew runClient` | Dev client, without the checks |

The first build downloads and decompiles Minecraft and takes 5-15 minutes.

## Docs

- [`CLAUDE.md`](CLAUDE.md): full command list and project rules.
- [`docs/superpowers/specs/`](docs/superpowers/specs/): designs.
- [`docs/superpowers/specs/evoking/evoking-functional-spec.md`](docs/superpowers/specs/evoking/evoking-functional-spec.md):
  everything Strength of Evoking does, in one document.
- [`docs/playtest-checklist.md`](docs/playtest-checklist.md): what to check by hand in game.

## License

[GPL-3.0-or-later](LICENSE). The vendored skills under `.claude/skills` are MIT-licensed; see
`.claude/skills/LICENSE-minecraft-agent-skills`.
