# Contributing

## Setup
JDK 21 and `make`. Clone, then run `make check` (first run: ~8 minutes while Minecraft is set up).
IntelliJ IDEA: open the folder as a Gradle project; run configurations come from ModDevGradle.

## Workflow
1. Branch from `main`: `fix/<slug>`, `security/<slug>`, `test/<slug>`, `ci/<slug>`, `docs/<slug>`, `chore/<slug>`.
2. Commit with [Conventional Commits](https://www.conventionalcommits.org/) in English (`fix(crafter): ...`).
3. Player-facing text: add the key to both `en_us.json` and `fr_fr.json`.
4. Run `make check` (and `make gametest` when you touch gameplay) before pushing.
5. Open a PR against `main` using the template and link the issue (`Closes #n`). The maintainer reviews and merges.

Never commit credentials or personal paths (`.vscode/` and `runs/` are ignored).

More context for agents and newcomers: [AGENTS.md](AGENTS.md).
