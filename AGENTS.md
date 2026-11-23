# AGENTS.md — Arffornia_Mods

Guide for AI agents and newcomers. Read this before changing anything.

## Purpose
NeoForge 1.21.1 mod (`modid` `arffornia`, package `fr.thegostsniperfr.arffornia`) for the Arffornia server and client:
milestone progression backed by the website API, Space Elevator, FE Crafter with milestone-unlocked recipes,
recipe banning, shop/vote rewards, FTB Teams sync, in-game progression viewer (website page in WebGUI), JEI.

## Repo map
| Path (under `src/main/java/fr/thegostsniperfr/arffornia/`) | What |
|---|---|
| `Arffornia.java`, `ArfforniaClient.java`, `Config.java` | Mod entry points, lifecycle events, config spec |
| `api/service/ArfforniaApiService.java`, `api/dto/` | HTTP client singleton (Gson) to the Laravel API, DTO records |
| `block/`, `block/entity/` | Crafter + Space Elevator (2-block: master + invisible part block), block entities |
| `screen/`, `client/screen/` | Menus (server) and screens (client) |
| `client/web/` | Progression viewer: the website page in WebGUI's Chromium (`WebProgression`, `WebProgressionScreen`) |
| `network/` | Payloads registered in `PacketHandler` (5 serverbound, 8 clientbound) |
| `progression/` | Server-side progression state: player attachment (active progression), `ProgressionStore` SavedData, `Progressions` refresh/sync, machine rules |
| `recipe/` | `arffornia:machine` recipe type (`MachineRecipe`, `ModRecipes`), `CrafterRecipes`; `recipe/pack/` downloads the website recipe datapack and loads it as a required pack |
| `command/` | `/arffornia progression ...` and `/arffornia shop ...` |
| `shop/` | `RewardHandler` (list, claim, deliver), `HttpRewardsClient` to `/api/rewards/*`, typed `RewardEntry`, `RewardDelivery` |
| `compat/` | Capabilities, FTB Teams events |
| `client/jei/` | JEI plugin, reads Crafter recipes from the synced `RecipeManager` |
| `src/main/resources/` | lang, models, blockstates, loot tables, recipes, tags |
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata (expanded from `gradle.properties`) |
| `earlywindow/` (Gradle subproject, own jar `arffornia-earlywindow.jar`) | Spec 007 early loading window: an FML `ImmediateWindowProvider` (`earlyWindowProvider = "arffornia"` in `config/fml.toml`) loaded on the SERVICE layer before Minecraft; no Minecraft classes. Its images are baked by Arffornia_Assets (`node early/window-line.mjs` for the PNG layers, then `node early/line.mjs` for the `line/` JPEG frames, a seamless 20/3 s loop at 12 fps); copy them from `out/early/` when the line or the mock-up changes |
| `.github/workflows/` | `ci.yml` (PR build), `build.yml` (tag release), `prerelease.yml` (`edge` and `pr-<n>` test pre-releases), `security.yml` (Trivy) |

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
  (recipe redesign #14, rewards #15, gating #16, machines/multiblocks #17).
- Unfixed security weaknesses: neutral issue/PR wording, no exploit details.
- Validate every serverbound payload on the server (open menu, distance, ownership, unlock state).

## Testing strategy
- GameTests (`@GameTestHolder(Arffornia.MODID)`) run by `runGameTestServer` against an in-memory progression backend
  (see #25, #26). Target behaviours: Crafter crafting/energy, payload rejection, elevator rules.
- JUnit unit tests for pure logic (energy maths, allow-lists, DTO parsing).
- No client GameTest framework in 1.21.1: UI checks are manual for now.

## Gotchas (from the audit)
- Recipes and bans come from the website datapack (`<server>/arffornia/recipe-pack.zip`), fetched at boot before the
  data packs load (15 s timeout, cached file otherwise, so starting never reloads) and by `/arffornia recipes reload`
  (the reload loads in the background, the server keeps ticking); an API outage keeps the last valid pack. Without any
  pack on disk nothing is banned.
- Recipes added by KubeJS or in code cannot be disabled by the datapack; remove them at their source.
- A machine belongs to the progression it was placed for (#18): linked synchronously from the placer's synced
  progression, it keeps it across team changes and stops while that progression is retired (team soft-deleted).
  `-1` (pre-#18 saves) means unlinked: pick it up and place it again. Gameplay reads `Progressions`, never HTTP.
- Spec 017 milestones are keyed by **key**, never row id. Gates are `arffornia:stage/<n>`, `arffornia:milestone/<key>`
  or the legacy `arffornia:milestone/<id>` (all digits, what today's datapack emits). A milestone is completed through
  one path only: the player targets it and delivers its items in the Space Elevator (or an admin runs
  `/arffornia progression add`). Owning or crafting an item never completes one. Both go through
  `ProgressionEventSender.milestoneCompleted`, which returns a `MilestoneResult` (rule 3 from the model read with its
  ETag into `ProgressionModels`); the Elevator consumes items only on `COMPLETED`. `arffornia:milestone/*` advancements
  in an older website datapack are inert (craft ones fail to parse and are skipped). The event waits in `ProgressionEventOutbox` (SavedData)
  until `POST /progressions/{id}/events` acknowledges it, with retry. Stages are opened by the website only.
- The progression key opens the website viewer in WebGUI's Chromium (`client/web/`, spec 018 D2 a). WebGUI is a required CLIENT dependency (`neoforge.mods.toml`, shipped by Provisioning); servers start without it. There is no other progression screen: when the browser runtime fails, the screen shows an error. `make run-client` needs the WebGUI 2.0.0 jar in `run/mods/` (it is `compileOnly`, so GameTests run without it). One browser per session, loaded on join at 1 fps, `webFps` (30) while open, closed on leaving. The page gets the player's read-only website token through `window.arfforniaQuery` (a CEF message router of ours, answered only for the page origin the server sent), never in a URL; never log it. Targets go page → `ServerboundTargetMilestoneKeyPacket` → rule 3 on the server (`ServerPayloadChecks.canTarget`) → website `set-target` by key; it is the only targeting payload. The page is zoomed by half the GUI scale, capped to keep at least 1280x720 CSS px (`PageZoom`, reapplied on every load end because Chromium drops a zoom set before load), and printable keys go to JCEF with their character (WebGUI 2.0.0 sends none, which JCEF on Linux turns into `Delete`).
- Item icons for the website come from the client itself: `-Darffornia.exportItemIcons=<dir>` renders every item at
  the title screen by registry id (`client/icon/ItemIconExport`), writes `manifest.json` and quits. Run it through
  Arffornia_Website `scripts/icons/export.sh`; renders that need a level or player are listed as `failed`.
- `space_elevator.json` needs SophisticatedStorage; FTB Teams/SophisticatedStorage are not declared in `mods.toml`.
- The early window extends FML 4.0.x `DisplayWindow` and replaces private fields by reflection (no theming API on 1.21.1).
  `ArfforniaEarlyWindowTest` fails when those fields move; after any NeoForge bump run
  `xvfb-run -a ./gradlew :earlywindow:visualTest` and look at `earlywindow/build/screens/`.
- `build.gradle` dev client run hardcodes a username/UUID.
- The COMMON config is shipped to clients too: never add secrets to SERVER-type configs (they sync to clients).

## Related repos
- `Arffornia_Website` — Laravel API and DB (`routes/api.php`), source of milestones/recipes/rewards.
- `Arffornia_Provisioning` — server/launcher config templates (`arffornia-common.toml`) and mod list.
- `Arffornia_Proxy` — Velocity plugin forwarding votes. `Nexus_Mods` — owner's mod tooling library.

## AI harness (agent-kit v2)
- `.claude/settings.json` (agent-kit plugin: hooks for fmt, `make check` and the end-of-task review gate; deny rules; sandbox), `.claude/rules/java-neoforge.md`. Harness friction or corrections → `/arffornia:harness-feedback`.
- `REVIEW.md` + `risk.yml` drive the depth of the end-of-task review (`/arffornia:review`, in the session; no AI review in CI); `GOLDEN_PRINCIPLES.md`; `PROGRESS.md` is the session handoff.
- Architecture guards: `src/test/java/.../architecture/ArchitectureTest.java` (runs in `make test`/`make check`);
  pre-existing violations are frozen in `src/test/resources/archunit_store/`, which may only shrink.
