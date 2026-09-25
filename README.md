# Raid Den Queue

A Fabric mod that adds a matchmaking queue on top of [Cobblemon Raid Dens](https://modrinth.com/mod/cobblemonraiddens):
pick a tier, wait for a party of up to 4, and launch straight into the raid together —
no need to find a natural den and coordinate everyone standing next to it.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| Java | 21+ |
| Fabric Loader | 0.17.3+ |
| Fabric API | 0.116.6+1.21.1 |
| Fabric Language Kotlin | 1.13.5+kotlin.2.2.10 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.8.0+ |
| [Cobblemon Raid Dens](https://modrinth.com/mod/cobblemonraiddens) | 0.12.0+ |
| Cloth Config | 15.0.140+ |

Exact pinned versions live in [`gradle.properties`](gradle.properties).

## Usage

- `/rqueue` — opens the tier-select menu, then a lobby screen showing who's queued for
  that tier. Click the green wool to join, and again (once you're in the queue) to
  launch the raid with whoever's currently waiting.
- `/rqueue back` — teleports you back to wherever you were standing before you entered
  a raid, in case something goes wrong (e.g. the raid ends and you don't get returned
  automatically).

Raids launch as soon as anyone in the queue clicks "start" — it doesn't wait for a full
party of 4. Leaving the queue (the red wool / closing the menu without joining) removes
you; disconnecting does too.

## Configuration

Settings live in `config/raid-den-queue.json`, created on first run:

```json
{
  "enableTierSix": false,
  "enableTierSeven": false,
  "maxPartySize": 4
}
```

- **`enableTierSix` / `enableTierSeven`** — Raid Dens ships tiers 1-5 out of the box;
  tiers 6 and 7 only exist if a datapack registers raid bosses for them. Setting either
  flag to `true` offers that tier in the queue, but *only* if Raid Dens actually has
  bosses registered for it — if no datapack adds tier 6/7 content, the flag has no
  effect and the tier stays hidden. This avoids exposing a tier players can queue for
  but that has no boss to fight.
- **`maxPartySize`** — capped at 4 (Raid Dens' own raid party limit).

The config is read on load; restart the server (or `/reload` won't pick it up — it's
plain JSON, not a datapack) after editing it.

## How raid entry works

Rather than requiring players to physically find and click a raid crystal together,
the queue drives Raid Dens' own internal raid-start sequence directly: a single
bookkeeping crystal is placed in a reserved, out-of-the-way pocket of the Overworld
(far from spawn, high in the sky — never on terrain anyone will encounter), a boss is
assigned to it, and every queued player is registered as a raid participant and
teleported straight into the raid dimension together. The crystal is automatically
removed once Raid Dens reports the raid as finished.

## Building

Raid Dens isn't published to a Maven repository this project can pull from, so its jar
has to be provided locally:

1. Download `cobblemonraiddens-fabric-<version>.jar` (matching `raiddens_version` in
   `gradle.properties`) from [Modrinth](https://modrinth.com/mod/cobblemonraiddens) or
   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/cobblemonraiddens).
2. Place it in `libs/` at the repo root.
3. Run `./gradlew build`.

If the jar is missing or its filename doesn't match `raiddens_version`, the build fails
immediately with a message telling you what's expected, instead of an opaque error
partway through compilation.

The built jar (and a sources jar) will be in `build/libs/`.
