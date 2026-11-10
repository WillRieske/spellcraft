# Spellcraft

A NeoForge mod for Minecraft that adds spells cast from scrolls. It has four:

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
  off for good, and otherwise the fight goes on as usual. Fangs spare you and your allies (your team, your pets, your own vexes,
  villagers, wandering traders, and iron golems that are not mad at you) and hurt everything else;
  vexes spare them too and fight only what the list above says. You, your pets, your fangs and your
  vexes cannot take an iron golem below 25 health; when it gets that low, it calms down and stops
  being mad at you. The keys rebind in Controls. Sneak + scroll or milk turns it off.

Each scroll is single-use. Scribe one by putting paper and lapis in an enchanting table: you get
a random scroll, and more lapis raises the odds of an extreme-tier spell. Creative players can
take any scroll from the Tools & Utilities tab, or use `/give @s spellcraft:<spell>_scroll`
(`upside_down`, `infinity`, `trailing` or `evoking`).

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
- [`docs/playtest-checklist.md`](docs/playtest-checklist.md): what to check by hand in game.

## License

[GPL-3.0-or-later](LICENSE). The vendored skills under `.claude/skills` are MIT-licensed; see
`.claude/skills/LICENSE-minecraft-agent-skills`.
