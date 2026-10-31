# PROGRESS.md — Arffornia_Mods

Handoff between sessions (humans and agents). Newest first; keep it under ~60 lines and move
finished multi-session work to `docs/exec-plans/completed/`.

## Now
- Harness v2 pilot (`chore/harness-v2`, stacked on the foundation PR #40): agent-kit settings,
  NeoForge rules, REVIEW.md, risk.yml, golden principles, ArchUnit guards with a frozen baseline.

## Next
- Land the open fix PRs (#41 payload validation, #42 reward allow-list, #43 HTTP timeouts, #44 async
  recipe load, #45 crafter energy, #46/#50 i18n); each one shrinks `src/test/resources/archunit_store/`.
- After #41: `ServerboundLaunchElevatorPacket` and `ServerboundSelectRecipePacket` leave the baseline.

## Verify
- `make check` (= `./gradlew build`, includes the ArchUnit guards in `test`), `make gametest`.

## Decisions waiting (needs-decision)
- Recipe source of truth #14, rewards #15, gating #16, machines/multiblocks #17, progression scope #18.
