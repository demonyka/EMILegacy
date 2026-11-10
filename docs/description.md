**EMI Legacy** is an unofficial port of [EMI](https://modrinth.com/mod/emi), the item and recipe viewer by Emi, to Minecraft 1.16.5 on Forge. It is EMI 1.1.24 with its interface, search, favorites and recipe tree, made to work on a version the original never supported.

This is not the original mod. Please report problems with the port to [its own issue tracker](https://github.com/demonyka/EMILegacy/issues), not to EMI.

**Install [JEI](https://modrinth.com/mod/jei) 7.7 or newer next to it** to see recipes of other mods. EMI itself only knows vanilla recipes; with JEI installed it reads every JEI recipe category, shows it in its own interface and hides JEI's.

## What you get

- **Item list and search**: `@mod`, `#tag`, `$tooltip`, regular expressions, and combinations of them.
- **Recipes and uses** of any item, for vanilla and for every mod with JEI support: machines, altars, multiblocks, whatever the mod shows in JEI.
- **Favorites**, including favorited recipes that show what you can craft right now.
- **Recipe filling**: the "+" button moves the ingredients into the crafting grid or machine, shift fills as many as possible. Works in modded machines through their JEI transfer handlers.
- **Recipe tree**: the full list of materials for a craft, with batch sizes, leftovers and the base cost.
- **Configuration screen** for everything: sidebars, themes, key binds, what is shown where.

## New in the port

- **Expand the whole tree**: one button resolves a recipe tree down to base resources. It follows the recipes of the mod an item comes from, prefers the crafting table, and does not go in circles through ore processing or storage blocks.
- **Applied Energistics 2**: inscriber presses are tools, the recipe tree counts them once.
- **Readable tag names**: "Quartz Dusts" instead of `#forge:dusts/quartz` for tags that mods ship without a name.
- **Hardened networking**: the recipe fill packet checks everything a client sends. Safe for public servers.

## Good to know

- Minecraft 1.16.5, Forge 36.2 or newer.
- Optional on both sides. A client with EMI can join a server without it, and the other way round. On a server it adds recipe filling through EMI's own packet and the `/emi` commands.
- Tested in a pack of 37 mods (Mekanism, Thermal, Immersive Engineering, Create, Botania, Applied Energistics 2, Refined Storage and others) and on a dedicated server.
- Do not install it together with Roughly Enough Items: REI replaces JEI. Use EMI with JEI, or REI alone.
- Differences from EMI on modern versions: no batched item renderer, no recipes for things newer than 1.16, text-only mod tooltips.

The list of tested mods and known issues is in [COMPATIBILITY.md](https://github.com/demonyka/EMILegacy/blob/master/docs/COMPATIBILITY.md).

## Credits

EMI was created by **Emi** and its contributors. Ported to 1.16.5 by **demonyga**. MIT licensed like the original, source on [GitHub](https://github.com/demonyka/EMILegacy).
