# EMI Legacy

Unofficial port of [EMI](https://github.com/emilyploszaj/emi) (item and recipe viewer by Emi, MIT) to older Minecraft versions on Forge.

This is not the original mod. Please do not report problems with this port to the EMI issue tracker.

| Version | Status |
|---|---|
| 1.16.5 Forge | release 1.1.24-legacy.1 |
| 1.12.2 Forge | planned, see [docs/PORTING.md](docs/PORTING.md) |
| 1.7.10 | not planned (no JEI; NEI handlers can't list all recipes) |

## Installing (1.16.5)

1. Minecraft 1.16.5 with Forge 36.2 or newer.
2. Put `emi-1.1.24-legacy.1+1.16.5+forge.jar` into `mods`.
3. Recommended: install [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) 7.7 or newer next to it (tested with 7.7.1.153 and 7.8.1.1018).

EMI itself only knows vanilla recipes. Recipes of other mods (machines etc.) come through JEI: EMI reads every JEI recipe
category, shows it in its own interface and hides JEI's interface. Without JEI, EMI works with vanilla recipes only.

EMI is optional on both sides. On a server it enables recipe filling through EMI's own packet and the `/emi` commands;
a client with EMI can join a server without it and the other way round.

Do not install EMI together with Roughly Enough Items: REI replaces JEI and draws its own item list.
Use EMI + JEI, or REI alone. Tested mods and known issues: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## What is different from EMI on modern versions

See [CHANGELOG.md](CHANGELOG.md). In short: no batched item renderer, no recipes for features newer than 1.16
(copper, mud), text-only mod tooltips, and JEI integration is limited to what JEI 7 offers.

## Building

Needs JDK 17 (build) and JDK 8 (running the game from the dev environment).

```sh
cd forge-1.16.5
./gradlew build        # release jar in build/libs
./gradlew runClient    # dev client
```

How the port is made, the test harness (`tests/*.emis`) and the plan for other versions: [docs/PORTING.md](docs/PORTING.md).

## License

MIT, same as EMI. Copyright (c) 2022 Emi; port changes by the EMI Legacy contributors. See [LICENSE](LICENSE).
