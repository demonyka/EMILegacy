# EMI Legacy changelog

## 1.1.24-legacy.1 (Minecraft 1.16.5, Forge 36.2+)

First release of the port of EMI 1.1.24 to Minecraft 1.16.5.

Works:
- Item list overlay, search (`@mod`, `#tag`, `$tooltip`, regex), favorites, recipe screen, recipe tree, config screen.
- Vanilla recipes: crafting, smelting/blasting/smoking/campfire, stonecutting, smithing, anvil, brewing, fuel, composting, world interactions.
- Recipes of other mods through JEI (install JEI 7.7+ next to EMI): every JEI category is shown in EMI and drawn by the mod's own JEI code; JEI's own interface is hidden.
- Recipe filling ("+" button, shift for max): vanilla crafting/furnaces natively, modded machines through their JEI transfer handlers.
- Dedicated servers.

Compatibility (tested with a 37-mod production pack, see docs/COMPATIBILITY.md):
- Item tooltips drawn by EMI fire Forge tooltip events, so AppleSkin, Quark and similar mods still draw on them.
- The mod name is not added twice when another mod (Jade, ...) already adds it.
- Roughly Enough Items replaces JEI with a stub and draws its own item list: use EMI + JEI or REI alone. EMI logs a warning and skips the JEI bridge when REI is present.

Differences from EMI on modern versions:
- The batched item renderer is not available (uses the regular renderer; the config option is hidden).
- Copper, waxing and mud world-interaction recipes do not exist in 1.16.5.
- No mod tooltip components (1.16 has text-only tooltips).
- JEI-only features without a JEI 7 equivalent: hiding stacks that are hidden in JEI, key presses inside JEI recipe areas.
