# EMI Legacy — порт EMI на старые версии Minecraft (Forge)

Оригинал: https://github.com/emilyploszaj/emi (MIT, автор — Emi). Лицензия сохранена в `LICENSE`.

## 1. Как устроен EMI

* ~32 тыс. строк, почти всё в общем модуле (`xplat`), платформенные модули тонкие (Forge ~550 строк).
* Пакеты:
  * `api` — публичный API для плагинов (`EmiPlugin`, `EmiRegistry`, `EmiStack`/`EmiIngredient`, `EmiRecipe`, виджеты).
  * `registry` — реестры стеков, рецептов, тегов, обработчиков заполнения рецептов (`EmiRecipeFiller`).
  * `screen` — весь UI: оверлей со списком предметов (`EmiScreenManager`, ~2000 строк), окно рецептов, дерево рецептов (BoM), конфиг.
  * `runtime` — перезагрузка в фоне (`EmiReloadManager`), избранное, скрытые стеки, логи.
  * `search` — поиск (`@мод`, `#тег`, `$tooltip`, regex).
  * `bom` — дерево рецептов / подсчёт стоимости.
  * `jemi` — **мост к JEI** (см. ниже).
  * `mixin` + `mixinsupport` — хуки в ванильные экраны и собственная система трансформаций EMI (`@Extends`, `@AdditionalField` через синтетический `GlobalMixin`).
  * `VanillaPlugin` — ванильные рецепты (верстак, печи, наковальня, варка, компост, топливо, взаимодействия с миром).

### Совместимость с другими модами (дробилки, компрессоры и т.д.)

EMI сам **не знает** про машины модов. Рецепты модов он получает двумя путями:

1. **Нативный EMI-плагин** мода (`@EmiEntrypoint` на Forge). На 1.16.5/1.12.2 таких модов нет — EMI тогда не существовал.
2. **JEMI** — EMI регистрирует себя как JEI-плагин, ждёт пока JEI загрузит плагины **всех** модов, затем забирает из JEI все категории, рецепты и катализаторы (машины), и оборачивает каждый рецепт в `JemiRecipe`. Отрисовку рецепта делает сама JEI-категория мода, поэтому любой мод с поддержкой JEI автоматически работает в EMI — с той же графикой (прогресс-бары, энергия, жидкости).

Вывод: на старых версиях **JEI = источник совместимости**. Почти все крупные моды 1.16.5 и 1.12.2 (Mekanism, Thermal, Create, Immersive Engineering, IC2, EnderIO, GregTech CE, AE2 и т.д.) имеют JEI-плагины. JEI ставится рядом с EMI, EMI скрывает интерфейс JEI и показывает всё в своём.

Без JEI EMI всё равно работает, но показывает только ванильные рецепты и рецепты из `RecipeManager` типов, которые он знает.

## 2. Версии

### 1.16.5 (основная) — делаем

| | |
|---|---|
| База | ветка `1.19.2` оригинала (EMI 1.1.24, Forge + JEMI, рендер на `MatrixStack`) |
| Сборка | Architectury Loom 1.7, Forge `1.16.5-36.2.39`, Yarn `1.16.5+build.10` (имена как в исходниках EMI) |
| Java | исходники на Java 17 синтаксисе, байткод Java 8 через Jabel (`switch ->`, `instanceof` с паттерном, records через `@Desugar`) |
| JEI | `jei-1.16.5:7.8.1.1018` (опционально; нужен для модов) |
| Mixin | есть в Forge 36.2.x |

Что уже сделано автоматически:
* Перемаппинг исходников Yarn 1.19.2 → 1.16.5 (`migrateMappings`): имена методов/полей. Имена классов в Yarn 1.16.5 build.10 уже совпадают с современными.
* Java 9+ API (`List.of`, `.toList()`, …) → Java 8 (`tools/java8-api.js`).

Что нужно портировать вручную:

| Блок | Суть |
|---|---|
| `TooltipComponent` | появился в 1.17. Бэкпорт интерфейса + собственный рендер тултипов (копия логики 1.19.2 на GL 1.16) |
| `TagKey`, `RegistryEntry` | появились в 1.18.2. Бэкпорт поверх `ITag`/`TagGroup` 1.16 |
| Рендер | 1.17+ шейдеры (`RenderSystem.setShader*`) → fixed-function 1.16; рендер предметов с `MatrixStack` через `RenderSystem.pushMatrix/multMatrix`; батчинг предметов (`StackBatcher`) — упростить/выключить |
| Экраны/виджеты | `addDrawableChild`/`Selectable`/narration → `addButton`/`children`; `getCursorStack` → `PlayerInventory#getCursorStack` |
| Forge-платформа | события 1.19 → 1.16 (`GuiScreenEvent`, `RenderTooltipEvent`), `ClientRegistry.registerKeyBinding`, `SimpleChannel`, поиск плагинов через `ModFileScanData` |
| JEMI | JEI 11 API (`IRecipeLayoutBuilder`, `RecipeType`) → JEI 7 API (`IRecipeLayout`, `IIngredients`, `getUid()`). Входы/выходы — через собственную реализацию `IIngredients`; отрисовка — `IRecipeLayoutDrawable.drawRecipe`; наведение — `getIngredientUnderMouse`; позиции слотов — из `IGuiIngredientGroup` |
| Скрытие JEI | в JEI 7 нет `registerRuntime`; оверлей JEI выключаем mixin'ом |
| Ванильные рецепты | кузнечный стол 1.16 (без шаблонов), нет меди/воска (`Oxidizable`, `HoneycombItem`) |

### 1.12.2 — реально, но отдельный проект (после 1.16.5)

* JEI для 1.12.2 (`jei_1.12.2:4.16.x`) даёт **всё нужное JEMI**: `IRecipeRegistry.getRecipeCategories / getRecipeWrappers / getRecipeCatalysts / createRecipeLayoutDrawable / getRecipeTransferHandler`. Значит машины модов будут работать так же, как на 1.16.5.
* Сборка: RetroFuturaGradle / CleanroomMC TemplateDevEnv (Java 8 + Jabel + MixinBooter).
* Дорого из-за базы: нет `MatrixStack`, `Text`/`OrderedText` (есть `ITextComponent`/строки), нет тегов (OreDictionary), метаданные предметов, GL11 напрямую, другой GUI-фреймворк, нет Mixin без MixinBooter.
* Переиспользуется: модель данных, реестры, поиск, BoM, избранное, конфиг (~50%). UI/рендер (~12 тыс. строк) — переписывать.
* Оценка: в 1.5–2 раза дороже порта 1.16.5.

### 1.7.10 — не рекомендую

* JEI нет. Стандарт — NEI (ChickenBones / форк GTNH). Моды регистрируют NEI-хендлеры (`TemplateRecipeHandler`), которые **отвечают на запросы** («рецепты для X»), а не отдают полный список. EMI же строит индекс всех рецептов заранее (поиск, дерево рецептов). Перечислить все рецепты через NEI можно только частично (`loadCraftingRecipes(overlayId)` поддерживают не все хендлеры).
* Плюс Java 8, LWJGL2, совсем старый рендер и GUI, Mixin только через UniMixins.
* Фактически это новый мод «EMI-подобный UI поверх NEI», а не порт. Для 1.7.10 у GTNH-форка NEI уже есть закладки, поиск и т.п.

## 3. План работ 1.16.5

1. [x] Сборка (Loom + Forge 1.16.5 + Jabel), перемаппинг, Java 8 API.
2. [ ] Бэкпорты: `TooltipComponent`, `TagKey`, `RegistryEntry`, рендер-примитивы (`EmiDrawContext`, `EmiPort`).
3. [ ] Экраны и виджеты, рендер предметов/жидкостей.
4. [ ] Forge-платформа: события, клавиши, сеть, mods.toml, поиск EMI-плагинов.
5. [ ] JEMI под JEI 7 + скрытие UI JEI + перенос рецептов (кнопка «+») через JEI transfer handlers.
6. [ ] Ванильный плагин под 1.16.5.
7. [ ] Запуск клиента, проверка с JEI и набором модов (Mekanism, Thermal, Create, IE).
