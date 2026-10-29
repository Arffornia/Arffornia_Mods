# AGENTS.md — Arffornia_Mods

Guide for AI agents and newcomers. Read this before changing anything.

## Purpose
NeoForge 1.21.1 mod (`modid` `arffornia`, package `fr.thegostsniperfr.arffornia`) for the Arffornia server and client:
milestone progression backed by the website API, Space Elevator, FE Crafter with milestone-unlocked recipes,
recipe banning, shop/vote rewards, FTB Teams sync, progression graph UI, JEI.

## Repo map
| Path (under `src/main/java/fr/thegostsniperfr/arffornia/`) | What |
|---|---|
| `Arffornia.java`, `ArfforniaClient.java`, `Config.java` | Mod entry points, lifecycle events, config spec |
| `api/service/ArfforniaApiService.java`, `api/dto/` | HTTP client singleton (Gson) to the Laravel API, DTO records |
| `block/`, `block/entity/` | Crafter + Space Elevator (2-block: master + invisible part block), block entities |
| `screen/`, `client/screen/` | Menus (server) and screens (client); `ProgressionGraphScreen` is the graph UI |
| `network/` | Payloads registered in `PacketHandler` (4 serverbound, 1 clientbound) |
| `recipe/` | `CustomRecipeManager` (server cache), `ClientRecipeCache`, `RecipeBanManager` (reload listener) |
| `command/` | `/arffornia progression ...` and `/arffornia shop ...` |
| `shop/` | `RewardHandler`, JDBC `DatabaseManager` to the website Postgres |
| `compat/` | Capabilities, FTB Teams events, JEI plugin |
| `src/main/resources/` | lang, models, blockstates, loot tables, recipes, tags |
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata (expanded from `gradle.properties`) |
| `.github/workflows/` | `ci.yml` (PR build), `build.yml` (tag release), `security.yml` (Trivy) |

## Commands
- `make check` (= `./gradlew build`), `make test`, `make gametest` (`runGameTestServer`), `make data`, `make run-client`.
- Gradle: always `--no-daemon -Dorg.gradle.jvmargs=-Xmx2g`; never run two Gradle builds at once. A cold build takes
  ~8 min (Minecraft decompilation); later builds are cached. Maven Central may answer 429: retry once after a pause.

## Conventions
- English for code, comments, logs, docs, commits, issues and PRs.
- Player-facing text goes through lang keys in `assets/arffornia/lang/en_us.json` **and** `fr_fr.json`
  (`Component.translatable`, styles via `ChatFormatting`, no `§` codes in new code).
- Conventional Commits (`fix(crafter): ...`); branches `fix/…`, `security/…`, `test/…`, `ci/…`, `docs/…`, `chore/…`.
- One PR per issue, `Closes #n`, body: What / Why / How it was tested / Risk & revert.
- World access only on the server thread: hop back with `server.execute(...)` / `thenAcceptAsync(..., server)` after HTTP.

## Hard rules
- No secrets in the repo (config defaults are placeholders; real values come from provisioning).
- Do not replace the owner's libraries (Nexus_*) with third-party ones.
- Never merge, tag, release or push to `main`; open PRs. Do not touch the release workflow's secret usage.
- Design decisions belong to the maintainer: issues labelled `needs-decision` are not to be implemented
  (recipe redesign #14, rewards #15, gating #16, machines/multiblocks #17, progression scope #18).
- Unfixed security weaknesses: neutral issue/PR wording, no exploit details.
- Validate every serverbound payload on the server (open menu, distance, ownership, unlock state).

## Testing strategy
- GameTests (`@GameTestHolder(Arffornia.MODID)`) run by `runGameTestServer` against an in-memory progression backend
  (see #25, #26). Target behaviours: Crafter crafting/energy, payload rejection, elevator rules.
- JUnit unit tests for pure logic (energy maths, allow-lists, DTO parsing).
- No client GameTest framework in 1.21.1: UI checks are manual for now.

## Gotchas (from the audit)
- Recipes come from the website over HTTP (server **and** every client); JEI can register 0 recipes if the fetch is late.
- Recipe bans fail open when the API is unreachable at datapack load.
- Crafters/Elevators store the placer's active progression id at placement (async); `-1` means unlinked.
- `space_elevator.json` needs SophisticatedStorage; FTB Teams/SophisticatedStorage are not declared in `mods.toml`.
- `build.gradle` dev client run hardcodes a username/UUID.
- The COMMON config is shipped to clients too: never add secrets to SERVER-type configs (they sync to clients).

## Related repos
- `Arffornia_Website` — Laravel API and DB (`routes/api.php`), source of milestones/recipes/rewards.
- `Arffornia_Provisioning` — server/launcher config templates (`arffornia-common.toml`) and mod list.
- `Arffornia_Proxy` — Velocity plugin forwarding votes. `Nexus_Mods` — owner's mod tooling library.

## AI harness (agent-kit v2)
- `.claude/settings.json` (agent-kit plugin, deny rules, fmt + `make check` hooks), `.claude/rules/java-neoforge.md`.
- `REVIEW.md` + `risk.yml` drive AI review depth; `GOLDEN_PRINCIPLES.md`; `PROGRESS.md` is the session handoff.
- Architecture guards: `src/test/java/.../architecture/ArchitectureTest.java` (runs in `make test`/`make check`);
  pre-existing violations are frozen in `src/test/resources/archunit_store/`, which may only shrink.
