# Spellcraft

NeoForge mod: Minecraft 26.3, NeoForge 26.3.0.23-beta (pinned; beta), Java 25, ModDevGradle.
Spec: `docs/superpowers/specs/2026-09-28-upside-down-design.md`.
Verified API facts: `docs/api-notes.md`. Read that before touching hooks, events or the Mixin.
Methodology: `~/.claude/development-methodology.md`.

## Commands

Run from the repo root. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`.

| Command | What |
|---|---|
| `./gradlew test` | Unit tests: JUnit 5 + jqwik + ArchUnit (seconds) |
| `./gradlew build` | spotlessCheck + Error Prone/`-Xlint:all -Werror` + tests + jar |
| `./gradlew spotlessApply` | Format (palantir-java-format) |
| `./gradlew runGameTestServer` | GameTests (server rules and common Mixins); non-zero exit on failure. |
| `./gradlew runData` | Headless client-dist boot (loads client classes and config specs); the only automated check of client code |
| `./gradlew runClient` | Dev client, for Will's playtest only |
| `./gradlew pitest` | Mutation testing of the pure core (on demand) |
| `./gradlew test jacocoTestReport` | Coverage report (on demand) |
| `python scripts/spell_icons.py` | Regenerate every spell's scroll and effect icon (one glyph per spell) |
| `python -m unittest discover -s scripts -p "test_*.py"` | Icon-script tests (also a pre-commit hook) |

A cold build (fresh clone, cleared Gradle cache, or a NeoForge bump) takes 5–15 min. Run it
by hand in the background, never through the commit gate (300 s limit).

## Minecraft sources: read, don't guess

NeoForge 26.3 is newer than most docs, so check every API against the real code:

```bash
MC_JAR="$(find build/moddev -name '*sources*.jar' | head -1)"
NF_JAR="$(find ~/.gradle/caches -name 'neoforge-26.3.0.23-beta-sources.jar' | head -1)"
mkdir -p build/mc-src build/nf-src
unzip -q -o "$MC_JAR" -d build/mc-src
unzip -q -o "$NF_JAR" -d build/nf-src
```

Then grep `build/mc-src` (vanilla `net.minecraft.*` with NeoForge patches applied) and `build/nf-src` (NeoForge events, registries, config) with the Grep tool.

## Rules

- **Visual verification belongs to Will.** Never claim a visual/feel behaviour works. Add or
  point to an item in `docs/playtest-checklist.md` and hand it over.
- **Server owns state; the client renders it.** The client only reads the vanilla-synced
  `spellcraft:upside_down` effect. No custom packets.
- **Client-only code lives under `io.github.willrieske.spellcraft.client`.** ArchUnit rule 1
  enforces this; a violation crashes dedicated servers.
- **Pure core** (`client.upsidedown`, `spell.rules`): no `net.minecraft`/NeoForge imports (ArchUnit rule 2). TDD
  it; add jqwik properties for invariants; `./gradlew pitest` after changing it and update `docs/mutation-testing.md`.
- **Mixins only where no event exists**, `require = 1`, handlers are thin and call shared code. Common
  Mixins live in `…spellcraft.mixin` (`spellcraft.mixins.json`), client ones in `…spellcraft.client.mixin`
  (`spellcraft.client.mixins.json`); a Mixin config's package holds only Mixins.
- **Error Prone runs with `-Werror`.** Suppress a check only with `@SuppressWarnings("<Check>")`
  plus a comment saying why (e.g. Mixin handlers → `UnusedMethod`).
- **Commits:** conventional (gitlint). The commit gate (`.claude/gate.json`) runs
  `spotlessCheck` and `build`; pre-push runs `runGameTestServer`.

## Skills

`.claude/skills/minecraft-modding` and `minecraft-testing` are vendored (see `PROVENANCE.md`).
They cover several MC versions; where they disagree with `docs/api-notes.md`, the notes win.
Their `scripts/` directories were omitted, so script mentions in them are dangling; ignore them.

## Reference docs

The NeoForge docs site is versioned 26.1, which is BEHIND our 26.3. Use these links for concepts and
paradigms only. For exact signatures, the decompiled sources in `build/mc-src` and `build/nf-src` and
`docs/api-notes.md` win.

- [NeoForge docs home](https://docs.neoforged.net/) - entry point.
- [Events](https://docs.neoforged.net/docs/concepts/events) - event bus, `@EventBusSubscriber`, mod vs game bus.
- [Sides](https://docs.neoforged.net/docs/concepts/sides) - logical/physical client and server, safe dist separation.
- [Registries](https://docs.neoforged.net/docs/concepts/registries) - `DeferredRegister`.
- [Mob effects](https://docs.neoforged.net/docs/items/mobeffects) - custom effects.
- [Items](https://docs.neoforged.net/docs/items/) - item behaviour and properties.
- [Configuration](https://docs.neoforged.net/docs/misc/config) - `ModConfigSpec`.
- [GameTests](https://docs.neoforged.net/docs/misc/gametest) - test environments, functions, instances.
- [Access transformers](https://docs.neoforged.net/docs/advanced/accesstransformers) - widening access.
- [Resources and data generation](https://docs.neoforged.net/docs/resources/) - assets, data, datagen.
- [Networking](https://docs.neoforged.net/docs/networking/) - we deliberately add none.
- [Mixin wiki](https://github.com/SpongePowered/Mixin/wiki) - Mixin fundamentals.
- [MixinExtras wiki](https://github.com/LlamaLad7/MixinExtras/wiki) - `WrapOperation` etc.
