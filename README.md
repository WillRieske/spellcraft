# Spellcraft

A NeoForge mod for Minecraft that adds spells cast from scrolls. It has four:

- <img src="src/main/resources/assets/spellcraft/textures/item/upside_down_scroll.png" alt="" width="24" height="24"> **Strength of Upside-Down**: for three minutes (configurable) your view rolls upside down while
  physics stays the same.
- <img src="src/main/resources/assets/spellcraft/textures/item/infinity_scroll.png" alt="" width="24" height="24"> **Strength of Infinity**: survival and adventure players get creative flight, the creative
  inventory and instant building, but still take damage, get hungry and can die. It has no time
  limit; only death or `/effect clear` removes it. If you die with Infinity, you keep your hotbar,
  main inventory and offhand through respawn (armor and XP are lost).
- <img src="src/main/resources/assets/spellcraft/textures/item/trailing_scroll.png" alt="" width="24" height="24"> **Strength of Trailing**: for eight minutes (configurable) you leave soul fire on most solid blocks
  you step off. It never hurts you, spares your allies (your team, your pets) while you are in the
  same dimension, and burns everything else; it spreads to nothing and lights no TNT. The trail is a fuse: the oldest flame goes out once a
  second (every two seconds over soul sand or soul soil), so it burns back toward you when you stop.
- <img src="src/main/resources/assets/spellcraft/textures/item/evoking_scroll.png" alt="" width="24" height="24"> **Strength of Evoking**: an evoker's powers, with no time limit. Press **F** to raise your arms
  and, a second later, carpet the 13×13 square ahead of you with evoker fangs (every block but your
  own; the square turns with you). Press **V** to call three vexes to your side for 30–120 s. Fangs and
  vexes spare you and your allies (your team, your pets, your own vexes) and hurt everything else; the
  vexes attack whatever comes within five blocks of them. The keys rebind in Controls; while Evoking is
  on, F casts instead of swapping hands. Sneak + scroll or milk turns it off.

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
| `./gradlew runClient` | Dev client, without the checks |

The first build downloads and decompiles Minecraft and takes 5-15 minutes.

## Docs

- [`CLAUDE.md`](CLAUDE.md): full command list and project rules.
- [`docs/superpowers/specs/`](docs/superpowers/specs/): designs.
- [`docs/playtest-checklist.md`](docs/playtest-checklist.md): what to check by hand in game.

## License

[GPL-3.0-or-later](LICENSE). The vendored skills under `.claude/skills` are MIT-licensed; see
`.claude/skills/LICENSE-minecraft-agent-skills`.
