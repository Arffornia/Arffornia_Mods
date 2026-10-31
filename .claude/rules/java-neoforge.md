---
paths:
  - "src/**/*.java"
  - "build.gradle"
  - "src/main/resources/**"
---

# NeoForge 1.21.1 conventions (Arffornia_Mods)

- **Serverbound payloads are hostile.** Every handler validates on the server before touching the
  world: the matching menu is open, the player is within 8 blocks, owns the block entity, and has
  the stage unlocked. Put checks in `network.ServerPayloadChecks` and call it; the ArchUnit guard
  (`ArchitectureTest.serverboundPayloadHandlersValidateOnTheServer`) fails a handler that does not.
- **No HTTP on the server thread.** API calls return `CompletableFuture`; never `join()`/`get()` in
  event handlers, ticks or commands. Hop back to the server thread with `server.execute(...)` or
  `thenAcceptAsync(..., server)` before touching levels, block entities, players or sending packets.
- **Client/common split.** Client-only code lives under `...arffornia.client` or in a
  `@EventBusSubscriber(value = Dist.CLIENT)` class. Common code never references
  `net.minecraft.client.*` (dedicated servers crash). Guarded by `commonCodeDoesNotReferenceClientOnlyCode`.
- **Player-facing text is translatable.** `Component.translatable("<area>.arffornia.<key>", args)`
  with the key in both `assets/arffornia/lang/en_us.json` and `fr_fr.json`; style with
  `ChatFormatting`, no `§` codes. `Component.literal` is guarded (`playerFacingTextIsTranslatable`).
- **Per-player/team state = data attachments.** Use `AttachmentType` via `DeferredRegister` with a
  codec (`.serialize(...)`, `copyOnDeath()` when it should survive) instead of static maps keyed by UUID;
  sync to the owner with one `playToClient` payload.
- **Own data files come from datagen.** Recipes, loot tables, tags, models and lang for the mod's own
  content are produced by `GatherDataEvent` providers into `src/generated/resources` (`make data`);
  do not hand-edit generated JSON. Optional mod dependencies get a `neoforge:mod_loaded` condition.
- **Registration** goes through `DeferredRegister` in the `Mod*` classes; never register in static
  initialisers of unrelated classes.
- **Config**: secrets only in server-side config (never in SERVER-type configs, which sync to clients).
- **Tests**: JUnit for pure logic (`make test`), GameTests for world behaviour (`make gametest`,
  skill `/arffornia:mc-gametest`). A guard failure message says how to fix it; do not edit
  `src/test/resources/archunit_store/` by hand (it only shrinks).
