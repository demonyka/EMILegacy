# EMI Legacy

Port of [EMI](https://github.com/emilyploszaj/emi) (item and recipe viewer, MIT) to older Minecraft versions on Forge.

| Version | Status |
|---|---|
| 1.16.5 Forge | works in dev: overlay, search, favorites, recipe screen; recipes of other mods via JEI (tested with Mekanism) |
| 1.12.2 Forge | planned, see [docs/PORTING.md](docs/PORTING.md) |
| 1.7.10 | not planned (no JEI; NEI handlers can't list all recipes) |

Recipes of other mods (machines etc.) come through JEI: install JEI next to EMI, EMI reads every JEI category and hides JEI's own UI.

Details, plan and known gaps: [docs/PORTING.md](docs/PORTING.md).
