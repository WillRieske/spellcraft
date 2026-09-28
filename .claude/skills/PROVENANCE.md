# Vendored skills

| Skill | Source | Commit | License |
|---|---|---|---|
| minecraft-modding | https://github.com/jahrome907/minecraft-agent-skills (`.claude/skills/minecraft-modding`) | dd57c5a97741cdc0eb5bb3a8f876581a4f09eeb0 | MIT (`LICENSE-minecraft-agent-skills`) |
| minecraft-testing | same repo (`.claude/skills/minecraft-testing`) | dd57c5a97741cdc0eb5bb3a8f876581a4f09eeb0 | MIT (`LICENSE-minecraft-agent-skills`) |

Reviewed line-by-line before install (2026-09-28). Only these 2 of the repo's skills are used.
Where they disagree with `docs/api-notes.md` (verified 26.3 facts), the notes win.

Each skill's `scripts/` directory was deliberately omitted (`check-build.sh` runs `./gradlew build`;
`validate-test-layout.sh` scans project files; both judged out of scope for a least-privilege
vendoring). Mentions of those scripts in `minecraft-testing/SKILL.md` (~line 29) and
`minecraft-testing/references/test-layouts.md` (~lines 102-103) are therefore dangling and must be
ignored. All other copied files are byte-identical to upstream.
