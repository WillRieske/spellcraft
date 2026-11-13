# Spellcraft

NeoForge mod: Minecraft 26.3, NeoForge 26.3.0.23-beta (pinned; beta), Java 25, ModDevGradle.
Spec: `docs/superpowers/specs/2026-09-28-upside-down-design.md`.
Evoking: `docs/superpowers/specs/evoking/evoking-functional-spec.md` says what the spell does, in numbered
paragraphs; the design specs beside it say how and why.
Verified API facts: `docs/api-notes.md`. Read that before touching hooks, events or the Mixin.
Methodology: `~/.claude/development-methodology.md`.

## Commands

Run from the repo root. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`.

| Command | What |
|---|---|
| `./gradlew test` | Unit tests: JUnit 5 + jqwik + ArchUnit (seconds) |
| `./gradlew build` | spotlessCheck + Error Prone/`-Xlint:all -Werror` + tests + jar |
| `./gradlew spotlessApply` | Format (palantir-java-format) |
| `./gradlew runGameTestServer` | GameTests (server rules and common Mixins); non-zero exit on failure. The task fails after 8 minutes (`build.gradle` `timeout`): a timeout most likely means a hung test (a test whose structure chunks never tick never starts and never times out). A full run took 2-4 minutes on 2026-11-12 (824 tests); raise the limit if the suite outgrows it. |
| `./gradlew runGameTestServer "-PgameTests=spellcraft:bound_vex_gear_*"` | Only the GameTests whose ID matches the selector (wildcards allowed), for the inner loop while writing a test. A selector that matches nothing fails. Never a substitute for the full run: a name cannot tell which tests a change affects, and the gates run every test. |
| `./gradlew runData` | Headless client-dist boot (loads client classes and config specs); the only automated check of client code, and it proves the `ArmPose` enum wiring (`EvokingArmPoseCheck`); it never fires `RegisterRenderers`, so no renderer, layer, model or particle provider is constructed and no texture is resolved: a green `runData` is no proof of the renderers |
| `./gradlew runClient` | Dev client, for Will's playtest only |
| `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1` | Gate checks + GameTests, then launch the dev client (or keep a current one); asks before stopping a stale one. Skips the checks and GameTests when `src/`, the Gradle files, the Evoking functional spec and the compiled classes are identical to the last passing run (`-Force` runs them anyway; use it after a change outside those, such as the JDK). `-CheckOnly`: status only (says whether the next run skips). Exit codes in the script header |
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "Import-Module Pester -RequiredVersion 3.4.0; Invoke-Pester -Path scripts/tests -EnableExit"` | Deploy-script unit tests (Pester 3.4, pinned because a newer Pester on the module path rejects `-EnableExit`; by hand, not in CI) |
| `./gradlew pitest` | Mutation testing of the pure core (on demand) |
| `./gradlew test jacocoTestReport` | Coverage report (on demand) |
| `python scripts/spell_icons.py` | Regenerate every spell's scroll and effect icon (one glyph per spell) |
| `python scripts/robe_texture.py` | Regenerate the Caster's robe texture (`bound_vex_robe.png`) |
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
- **Server owns state; the client renders it.** The client reads vanilla-synced state (effects, the
  effect-particle list) and never decides anything. The one custom packet is Evoking's
  client→server `EvokingRequest` ("this key was pressed"; two request kinds: cast, team move), which
  the server validates in full; nothing is sent clientbound. Add no other packets without a spec that
  says why vanilla's sync cannot carry the state.
- **Client-only code lives under `io.github.willrieske.spellcraft.client`.** ArchUnit rule 1
  enforces this; a violation crashes dedicated servers.
- **Pure core** (`client.upsidedown`, `spell.rules`): no `net.minecraft`/NeoForge imports (ArchUnit rule 2). TDD
  it; add jqwik properties for invariants; `./gradlew pitest` after changing it and update `docs/mutation-testing.md`.
- **Mixins only where no event exists**, `require = 1`, handlers are thin and call shared code. Common
  Mixins live in `…spellcraft.mixin` (`spellcraft.mixins.json`), client ones in `…spellcraft.client.mixin`
  (`spellcraft.client.mixins.json`); a Mixin config's package holds only Mixins.
  `PlayerGhostrailMixin` (Ghostrail's `noPhysics`) is the one Mixin Part 7 adds; the Part 6 packet rule is unchanged: Part 7 adds no packet.
- **Error Prone runs with `-Werror`.** Suppress a check only with `@SuppressWarnings("<Check>")`
  plus a comment saying why (e.g. Mixin handlers → `UnusedMethod`).
- **Evoking paragraph numbers.** Code and tests that implement a paragraph of the Evoking functional spec name it
  in a comment, written exactly `Functional spec §3.4` (a list is `Functional spec §3.4, §3.5`; no ranges). When
  Evoking behavior changes, update the functional spec and the comments in the same change; add a new number at the
  end of a section instead of renumbering. `FunctionalSpecReferencesTest` fails on a number the spec does not have
  and on a paragraph no code under `src/main` names.
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
- [Networking](https://docs.neoforged.net/docs/networking/) - one client-to-server packet only (Evoking's `EvokingRequest`).
- [Mixin wiki](https://github.com/SpongePowered/Mixin/wiki) - Mixin fundamentals.
- [MixinExtras wiki](https://github.com/LlamaLad7/MixinExtras/wiki) - `WrapOperation` etc.
