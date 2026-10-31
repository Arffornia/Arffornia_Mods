# Arffornia (NeoForge mod)

Official mod of the Arffornia Minecraft server (MC **1.21.1**, NeoForge **21.1.x**), installed on both the server and
the client. It implements the progression gameplay: a graph of milestones (stages, unlocks, requirements) stored and
edited on the Arffornia website, the **Space Elevator** that completes milestones from delivered items, the FE-powered
**Crafter** that runs the custom recipes milestones unlock, recipe banning for gated items, shop/vote reward claiming,
FTB Teams sync, a progression graph screen (key `G`), a HUD target label and JEI integration.
It talks to the Laravel website API (`ApiConfig.baseUrl`) over HTTP.

Architecture docs (coming soon): https://arffornia.github.io/docs/

## Requirements
- JDK 21 (Temurin recommended). Gradle is provided by the wrapper.
- ~2 GB of RAM for Gradle; the first build downloads and decompiles Minecraft (several minutes).
- Optional at runtime: FTB Teams (team sync), JEI (recipe view), SophisticatedStorage (Space Elevator recipe).

## Quickstart
```sh
make check          # ./gradlew build: compile, tests, jar
make run-client     # dev client (runs/ is git-ignored)
make run-server     # dev dedicated server
```
The jar is written to `build/libs/arffornia.jar` (PostgreSQL driver and HikariCP are embedded via jarJar).

## Commands
| Command | What it does |
|---|---|
| `make check` | `./gradlew build` (what CI runs) |
| `make build` | Build the jar only |
| `make test` | JUnit tests |
| `make gametest` | `runGameTestServer`, headless GameTests |
| `make data` | Datagen into `src/generated/resources` |
| `make run-client` / `make run-server` | Dev runs |

All targets cap Gradle at 2 GB and disable the daemon; override with `GRADLE_ARGS=...`.

## Configuration
One COMMON config file, `config/arffornia-common.toml`:

| Section / key | Meaning |
|---|---|
| `api.baseUrl` | Website API base URL (prod `https://arffornia.com/api`) |
| `api.clientId`, `api.clientSecret` | Website service-account credentials (server only; leave empty on clients) |
| `api.migrateOnStartup` | Reverse-engineer and upload recipes for unlocked items at server start |
| `database.host/port/database/username/password` | Website PostgreSQL, used for shop/vote rewards (server only) |

In production the server file is templated by
[Arffornia_Provisioning](https://github.com/Arffornia/Arffornia_Provisioning) (`config/server/...`) and the client file
ships with empty secrets (`config/launcher/...`). Never commit real values.

## In-game commands
`/arffornia progression ...` (admin: add/remove/list milestones, `addunlock`, set requirements, move items) and
`/arffornia shop claim_reward` (players). Permissions are checked in `util/Permissions.java`.

## Release process
Bump `mod_version` in `gradle.properties`, merge to `main`, then push a tag `vX.Y.Z`. The `Build` workflow runs
`./gradlew githubRelease`, which attaches `arffornia.jar` to a GitHub release. Provisioning downloads that asset.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md). Security issues: [SECURITY.md](SECURITY.md).

## License
`mod_license` is "All Rights Reserved" and there is no LICENSE file yet; a decision is pending in #19.
`TEMPLATE_LICENSE.txt` is the NeoForge MDK template's license, kept until that decision.
Minecraft mappings are covered by the [Mojang mappings license](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
