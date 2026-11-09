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
2. [x] Бэкпорты (`dev.emi.emi.backport`): `TooltipComponent` + `TooltipRenderer`, `TagKey`, `ModelViewStack`, `CompatScreen`; `EmiPort` под 1.16.5.
3. [x] Экраны и виджеты, рендер предметов/жидкостей.
4. [x] Forge-платформа: события, клавиши, сеть, mods.toml, поиск EMI-плагинов.
5. [x] JEMI под JEI 7 + скрытие UI JEI + кнопка «+» через JEI transfer handlers.
6. [x] Ванильный плагин под 1.16.5 (без меди/воска/грязи).
7. [x] Проверка в игре: dev + продакшен, см. ниже.

### Проверено (релиз 1.1.24-legacy.1)

Две среды: dev-клиент (Loom) и **продакшен** — собранный jar в обычном Forge 1.16.5-36.2.39 (клиент с настоящим рендером + выделенный сервер).
Моды: JEI 7.8.1, Mekanism 10.1.2, Immersive Engineering 5.1.0, Botania 420.3, Thermal Expansion/Foundation 1.5 (только продакшен).

* Загрузка без ошибок, все mixin-ы применяются; в продакшен-логе 0 предупреждений и ошибок EMI.
* JEMI: 92 категории JEI (Mekanism, Thermal, IE, Botania, ваниль); рецепты машин рисует JEI-код мода (энергия, жидкости, газы, шансы, 3D-модели).
* Оверлей, поиск (`@мод`, подсветка синтаксиса), R/U по предмету, избранное (A), рецепт по умолчанию, дерево рецептов, конфиг.
* «+»: сетка инвентаря 2×2, верстак, Formulaic Assemblicator Mekanism (через JEI transfer handler), на выделенном сервере через пакет EMI.
* Эффекты зелий не перекрывают панели EMI (зона исключения из VanillaPlugin), отдельный mixin не нужен.

### Найдено и исправлено при тестах

* Поиск не получал фокус: в 1.16 `TextFieldWidget` меняет фокус в обход переопределения EMI.
* Крах в продакшене: EMI логировал через SLF4J, которого нет в Forge 1.16.5 (в dev он есть) → Log4j.
* Дубли и невалидные id рецептов из JEI (стрелы, рагу, шалкеры, Mekanism rotary) и варки Forge.
* Тег-модели EMI ссылались на медные блоки 1.17.

### Ограничения

* Пакетный рендер (`StackBatcher`) недоступен, опция скрыта.
* Нет рецептов меди, воска и грязи (их нет в 1.16.5), нет tooltip-компонентов модов (в 1.16 тултипы только текстовые).
* Функций JEI 11, которых нет в JEI 7, нет: скрытие стеков, скрытых в JEI; клавиши внутри JEI-рецептов.
* Tinkers' Construct и CoFH-моды не запускаются в Loom dev-окружении (ремаппинг/рефлексия), проверяются только в продакшене.

## 4. Разработка

```sh
cd forge-1.16.5
./gradlew build                      # релизный jar в build/libs
./gradlew runClient                  # dev-клиент (Java 8 подхватывается автоматически)
./gradlew compileJava -PcoreOnly     # собрать без JEMI
cd ..
tools/run-script.sh tests/01-tour.emis -PtestMods   # dev-клиент + сценарий; скриншоты в forge-1.16.5/run/screenshots
HMC=<dir> tools/run-prod.sh tests/06-prod.emis     # релизный jar в настоящем Forge (см. tools/launch-prod.js)
```

Сценарии тестов — `tests/*.emis`; команды описаны в `EmiDevScript` (мир/сервер, команды, клики, клавиши, ввод, скриншоты, дамп слотов). Харнесс неактивен без `-Demi.devScript`.

Нужны JDK 17 (сборка) и JDK 8 (запуск игры). `tools/jp.sh` — javap по Minecraft/Forge/JEI с Yarn-именами; `tools/*.js` — скрипты механического портирования.
