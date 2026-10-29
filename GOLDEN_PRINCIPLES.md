# Golden principles — Arffornia_Mods

Opinionated rules that keep this mod legible for humans and agents. A principle that is broken
twice gets a sensor (ArchUnit guard, test, CI check). Each line names its sensor, or "review".

1. **The server decides.** Clients ask; serverbound handlers validate everything (menu, distance,
   ownership, unlock). Sensor: ArchUnit `serverboundPayloadHandlersValidateOnTheServer`.
2. **The server thread never waits on the network.** Async HTTP, then hop back. Sensor: review (+ GameTests).
3. **A dedicated server never loads client classes.** Sensor: ArchUnit `commonCodeDoesNotReferenceClientOnlyCode`.
4. **Every word a player reads is translated (en + fr).** Sensor: ArchUnit `playerFacingTextIsTranslatable`, lang lint.
5. **The website is the source of truth for progression; the mod caches, never invents.** Sensor: review.
6. **Data over code.** Recipes, loot and tags are data files (datagen for our own); behaviour reads them. Sensor: `make data` diff in CI (planned).
7. **Fail closed.** If the API is unreachable, locked content stays locked. Sensor: GameTest (planned).
8. **Debt only shrinks.** Baselines (`archunit_store/`) may lose lines, never gain them. Sensor: review + AI review rule.
9. **Prove it.** A change ships with the test that fails without it and the `make check` output. Sensor: Stop hook, PR template.
