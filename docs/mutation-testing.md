# Mutation testing (PIT) — pure core

Scope: `io.github.willrieske.spellcraft.client.upsidedown.*`. Run: `./gradlew pitest`.

| Date | Mutation score | Line coverage | Known survivors (equivalent mutants) |
|---|---|---|---|
| 2026-09-28 | 94% (61/65 killed) | 94% (51/54) | `UpsideDownRollState:44` — ConditionalsBoundaryMutator (`graceTicks > 0` to `>= 0`) — the counter then only decrements below zero, which every later `graceTicks > 0` check treats the same. `UpsideDownRollState:83` — ConditionalsBoundaryMutator (`distance < EPSILON` to `<=`) — needs a distance of exactly 1e-9, unreachable. `UpsideDownRollState:84` — NegateConditionalsMutator and VoidMethodCallMutator (no coverage) — defensive zero-distance guard in `startRoll`; `advance()` settles a finished roll before any new one starts, so a roll never begins at its own target. |
| 2026-09-28 (final, Task 11) | 94% (61/65 killed) | 94% (51/54) | Same four non-killed mutants as the row above, unchanged: `UpsideDownRollState:44` and `:83` (ConditionalsBoundaryMutator) and `:84` (NegateConditionalsMutator, VoidMethodCallMutator; no coverage), all justified there as equivalent or unreachable. No new survivors. |
