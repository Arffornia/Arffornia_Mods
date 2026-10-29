# REVIEW.md — Arffornia_Mods

How to review a change in this repo, for AI reviewers (`/arffornia:review`, the
`arffornia:reviewer` agent, `.github/workflows/claude-review.yml`) and humans.

## Output
- First line: `Findings: N (blocking b, major m, minor k) · risk: high|medium|low`.
- Every finding cites `path:line` and the evidence (quoted code, or a command and its output).
  No evidence: ask a question instead.
- At most 3 style remarks. Never approve; say what you did not verify.

## Depth by risk (`risk.yml` drives the CI depth)
| Risk | Paths | Depth |
|---|---|---|
| High | `network/` payload handlers, `shop/` (rewards, JDBC), `api/` (auth, HTTP), `command/`, config, `build.gradle`, workflows | Every line, trace callers, try to break it from a modified client |
| Medium | block entities, menus, recipes, compat, client screens | Every line, check GameTests/unit tests cover it |
| Low | lang files, textures, docs, tests only | Skim |

## Always check
- Serverbound handlers call `ServerPayloadChecks` (menu open, distance, ownership, unlock) before acting.
- No `join()`/`get()` on the server thread; HTTP callbacks hop back with `server.execute` before world access.
- Common code does not reference `net.minecraft.client.*`; client code stays under `client/` or `Dist.CLIENT`.
- Player text uses lang keys present in `en_us.json` and `fr_fr.json`; no new `Component.literal`.
- No secrets or real hosts in config defaults; nothing sensitive in configs synced to clients.
- `src/test/resources/archunit_store/` only shrinks (a growing store hides a new violation).
- `./gradlew build` output in the PR; behaviour changes link an issue.

## Skip
- `src/generated/resources/**` (datagen output: review the provider instead), `gradle/wrapper/**`, `build/`.
