# EMI Legacy changelog

## 1.1.24-legacy.1 (Minecraft 1.16.5, Forge 36.2+)

First release of the port of EMI 1.1.24 to Minecraft 1.16.5.

Works:
- Item list overlay, search (`@mod`, `#tag`, `$tooltip`, regex), favorites, recipe screen, recipe tree, config screen.
- Vanilla recipes: crafting, smelting/blasting/smoking/campfire, stonecutting, smithing, anvil, brewing, fuel, composting, world interactions.
- Recipes of other mods through JEI (install JEI 7.7+ next to EMI, tested with 7.7.1.153 and 7.8.1.1018): every JEI category is shown in EMI and drawn by the mod's own JEI code; JEI's own interface is hidden, and requests to open JEI's recipe screen (progress arrows of machine screens, JEI key binds) open EMI instead.
- Recipe filling ("+" button, shift for max): vanilla crafting/furnaces natively, modded machines through their JEI transfer handlers.
- Tags: tag ingredients, `#tag` search, tag recipes.
- Dedicated servers. EMI is optional on both sides (servers without EMI are not shown as incompatible).

Compatibility (tested with a 37-mod production pack, see docs/COMPATIBILITY.md):
- Item tooltips drawn by EMI fire Forge tooltip events, so AppleSkin, Quark and similar mods still draw on them.
- The mod name is not added twice when another mod (Jade, ...) already adds it.
- Roughly Enough Items replaces JEI with a stub and draws its own item list: use EMI + JEI or REI alone. EMI logs a warning and skips the JEI bridge when REI is present.

New compared to EMI 1.1.24:
- Recipe tree: an "expand the whole tree" button (bottom right, next to help). It picks a recipe for every ingredient that can only be made one way, level by level; ingredients with several recipes (ingots, gems, tags) are left to choose by hand. Shift-click goes back to the default recipes.

Hardening compared to EMI 1.1.24:
- The recipe fill packet bounds every list it reads (one packet from any player could exhaust server memory or stall the server tick), is ignored for spectators and for containers the player may not use, skips slots that do not give up their items (ghost slots of mods), takes partial stacks through the slot so modded inventories are told, and returns items to the player when a slot of a mod throws.
- Chess invites are rate limited per player, creative item requests are clamped to the maximum stack size.
- A reload requested right after leaving a world is no longer lost (EMI could stay empty or stuck on "Loading" after a quick rejoin).
- Errors while drawing EMI over a container screen are logged instead of crashing the game.

Adapted to 1.16.5:
- The item on the cursor is kept when an EMI screen opens over a container.
- Status effects stay where vanilla 1.16 draws them (left of the inventory) and EMI sidebars move out of their way; there is no "effect location" option.
- Stripping, tilling and path recipes list every axe, hoe and shovel (1.16 has no tool tags).
- Recipe tree line colors, button tint.

Differences from EMI on modern versions:
- The batched item renderer is not available (uses the regular renderer; the config option is hidden).
- Copper, waxing and mud world-interaction recipes do not exist in 1.16.5.
- No mod tooltip components (1.16 has text-only tooltips).
- JEI-only features without a JEI 7 equivalent: hiding stacks that are hidden in JEI, key presses inside JEI recipe areas.
