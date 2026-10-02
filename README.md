# Spellcraft

A NeoForge mod that adds spells cast from scrolls. The first is **Strength of Upside-Down**: for
three minutes your view rolls upside down while physics stays the same.

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
