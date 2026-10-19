# MDM Dye Depot Compat

A NeoForge compatibility mod for **Minecraft 1.21.1** that lets the
[Music Disc Maker](https://modrinth.com/mod/music-disc-maker) disc-dyeing table
accept the extra dyes added by [Dye Depot](https://modrinth.com/mod/dye-depot),
and gives each Dye Depot color its own distinct disc tint.

Without this mod, the disc-dyeing table silently ignores Dye Depot dyes because
Music Disc Maker only knows about the 16 vanilla dye colors. With it, all 16
Dye Depot colors (amber, aqua, beige, coral, forest, ginger, indigo, maroon,
mint, navy, olive, rose, slate, tan, teal, verdant) work in the table and render
as unique colors on the disc label.

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.262+ |
| [Music Disc Maker](https://modrinth.com/mod/music-disc-maker) | 3.0.0+ |
| [Dye Depot](https://modrinth.com/mod/dye-depot) | 2.0.0+ |

Both Music Disc Maker and Dye Depot are **required** — this mod does nothing on
its own.

## Installation

Download the latest `mdmdyedepotcompat-<version>.jar` from the
[GitHub Releases page](https://github.com/A-Little-Bit-of-Everything/MDMDyeDepotCompat/releases)
(current release:
[0.1.8](https://github.com/A-Little-Bit-of-Everything/MDMDyeDepotCompat/releases/tag/0.1.8))
and drop it into your `mods/` folder alongside Music Disc Maker and Dye Depot.

## Modrinth

- **Project page:** [modrinth.com/mod/mdm-dye-depot-compat](https://modrinth.com/mod/mdm-dye-depot-compat)
  _(pending approval)_
- **Download:** grab the latest jar from the project's **Files** tab once it is live,
  or from the [GitHub Releases page](https://github.com/A-Little-Bit-of-Everything/MDMDyeDepotCompat/releases)
  in the meantime.

> Music Disc Maker and Dye Depot are listed as **required** dependencies; the mod is
> tagged for Minecraft 1.21.1 / NeoForge.

## Multiplayer

Install this mod on the **server and every client**.

- The dyeing operation runs server-side, so the server needs it to produce the
  correctly colored disc.
- The disc tint is rendered client-side, so each client needs it to display the
  Dye Depot colors. A client without the mod will see incorrect tints.

The disc's color is stored as a string id (e.g. `forest`, `navy`) in its data
component, so it survives save/load and network transfer between modded clients.

## How It Works

Music Disc Maker stores its dye colors in a `DiscDye` enum with fixed HSL
values, and resolves a dye item's color by name via `DiscDye.byId(name)`. Dye
Depot's dyes are real `DyeItem`s whose color names (`forest`, `navy`, …) simply
aren't in that enum, so `byId` returns `null` and no dyeing happens.

At `FMLCommonSetupEvent`, this mod:

1. **Injects the 16 Dye Depot colors into `DiscDye.VALUES`** with HSL values
   derived from each dye's primary RGB color. Because Java forbids creating
   enum instances reflectively, the entries are allocated with
   `sun.misc.Unsafe.allocateInstance` and their fields populated via field
   offsets. Music Disc Maker's `DiscPalette` generates the disc tint purely from
   these HSL fields, so each color comes out unique.

2. **Resizes `DiscDyeTint`'s color caches** (client only). That class sizes a
   `16 × 16` color cache from the original enum and indexes it by dye ordinal;
   the injected dyes (ordinals 16–31) would overflow it and crash the screen, so
   the caches are rebuilt to match the new dye count.

If either step fails (for example after a Music Disc Maker update that changes
internal field names), the error is logged and the game continues running — the
disc-dyeing table just won't recognise Dye Depot dyes until the mod is updated.

## Building From Source

```bash
./gradlew build
```

The built jar is placed in `build/libs/`.

The Music Disc Maker and Dye Depot jars are referenced as compile-only
dependencies via paths in `gradle.properties`
(`music_disc_maker_jar` and `dye_depot_jar`) — update those paths to point at
your local copies before building.

## Generating a Custom Disc Image

The special **`-logo`** build (e.g.
[`mdmdyedepotcompat-0.1.8-logo.jar`](https://github.com/A-Little-Bit-of-Everything/MDMDyeDepotCompat/releases/tag/0.1.8))
includes a one-shot utility that renders a custom music-disc texture for any
board + accent colour pair and saves it as a PNG. This is how the project logo
(indigo board, forest accent) was produced.

It is **not** part of the normal release — use the `-logo` jar only when you want
to generate an image, then switch back to the regular jar.

1. Put `mdmdyedepotcompat-<version>-logo.jar` in your `mods/` folder (alongside
   Music Disc Maker and Dye Depot, as usual).
2. Add this JVM argument to your launcher, where the two values are the **board**
   and **accent** colour ids:

   ```
   -Dmdmddc.dumpLogo=indigo,forest
   ```

   In PrismLauncher: **Edit Instance → Settings → Java → JVM arguments**. Any of
   the 16 vanilla dye names or the 16 Dye Depot names (amber, aqua, beige, coral,
   forest, ginger, indigo, maroon, mint, navy, olive, rose, slate, tan, teal,
   verdant) may be used.
3. Launch the game and wait until the main menu appears (no world needs to be
   loaded — the texture is composited from the in-game resources once they are
   available).
4. The image is written to:

   ```
   <instance>/minecraft/mdmddc_logo/disc_<board>_<accent>.png
   ```

   It is a 512 × 512 PNG (the 16 × 16 disc scaled up 32× with nearest-neighbour,
   so it stays crisp pixel art). The colours are produced by Music Disc Maker's
   own `DiscDyeTint`, so the output matches the in-game disc exactly.
5. Remove the `-Dmdmddc.dumpLogo` argument and swap the `-logo` jar back out for
   the normal release jar when you are done.

## Known Limitations

- Relies on reflection/`Unsafe` against Music Disc Maker's internal classes
  (`DiscDye`, `DiscDyeTint`). A future Music Disc Maker update that renames or
  restructures those may require an update here. Failures degrade gracefully
  (logged, no crash) rather than breaking the game.
- `sun.misc.Unsafe` is deprecated; this targets Java 21 where it remains
  available.

## Credits

- **TheAndromedaCat**
- A Little Bit of Everything Team
- Not a Glitch Studios

This is an unofficial compatibility mod and is not affiliated with the authors
of Music Disc Maker or Dye Depot.
