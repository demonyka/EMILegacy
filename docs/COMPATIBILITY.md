# Совместимость EMI Legacy 1.16.5 с другими модами

Проверено 10.11.2026 в продакшене: релизный jar в обычном Forge 1.16.5-36.2.39 (клиент с настоящим рендером, Java 8), сценарии `tests/08-pack.emis`, `09-tooltips`, `10-use`, `12-rei`, `13-state`.

## Сборка (37 модов)

JEI 7.8.1, Mekanism 10.1.2, Thermal Expansion/Foundation 1.5 + CoFH Core, Immersive Engineering 5.1.0, Botania 420.3 + Patchouli + Curios,
Create 0.3.2g + Flywheel, Applied Energistics 2 8.4.7, Refined Storage 1.9.18, Quark r2.4 + AutoRegLib, Mouse Tweaks, Just Enough Resources,
AppleSkin, Sophisticated Backpacks, Iron Chests, Storage Drawers, Cooking for Blockheads, Mystical Agriculture + Cucumber,
Industrial Foregoing + Titanium, Silent Gear + Silent Lib, Jade, Controlling, Rubidium, CraftTweaker, Tinkers' Construct + Mantle,
Waystones, Pam's HarvestCraft 2 Food Core. Отдельно: Twilight Forest, Roughly Enough Items.

## Итог

| Что | Результат |
|---|---|
| Загрузка | без ошибок; 0 предупреждений/ошибок EMI в логе |
| Mixin-конфликты | нет. Те же классы правят Quark (`Screen`, `ContainerScreen`, `ItemRenderer`, `ItemStack`), Rubidium (`ItemRenderer`), Create/Flywheel/IE/Rubidium (`Minecraft`) — разные методы, всё применяется |
| Рецепты модов (JEMI) | все категории JEI: Create (3D-анимации), Tinkers (жидкости, температура), Industrial Foregoing, AE2, Mystical Agriculture, JER (генерация мира), Botania, Thermal, Mekanism, IE, Twilight Forest |
| Перенос рецептов «+» | ванильный верстак и инвентарь; Mekanism Formulaic Assemblicator через JEI transfer handler (тот же механизм у RS/AE2) |
| Клавиши | клавиши EMI — свои (`config/emi.css`), действуют только поверх EMI и слотов; R/U/A над слотом обрабатывает EMI, а не JEI |
| Оверлеи | кнопки Quark/Curios, эффекты зелий не перекрываются с панелями EMI |
| Рендер | Rubidium — без артефактов |
| Производительность | 30 900 рецептов, 1128 тегов; перезагрузка EMI 9–15 с, из них ~9 с — ожидание загрузки самого JEI |

## Найдено и исправлено

1. **Тултипы модов не рисовались в тултипах EMI** (иконки сытости AppleSkin, превью Quark и т.п.). В 1.16 они рисуются через Forge `RenderTooltipEvent`, а EMI рисует тултип сам. Теперь EMI посылает `Pre`/`Color`/`PostBackground`/`PostText` для тултипов предметов.
2. **Название мода дублировалось** в тултипах (Jade и др. тоже его добавляют, с legacy-кодами цвета). EMI больше не добавляет его повторно.

## Независимое ревью перед релизом

Три независимых ревью кода (мост JEI; платформа, сеть и mixin-ы; бэкпорты и интерфейс) нашли и помогли исправить:

1. **В одиночной игре с JEI EMI загружался без тегов** (0 тегов до `/reload`): перезагрузка EMI стартовала раньше, чем клиент получал теги. Теперь при встроенном сервере теги берутся у сервера. Проверка: `tests/13-state.emis` (190 тегов в ванили, 1128 в сборке).
2. **Пакет заполнения рецепта позволял уронить сервер** одним пакетом с огромным диапазоном слотов (унаследовано от оригинала). Добавлены ограничения, запрет для наблюдателей и защита от «призрачных» слотов модов.
3. **JEI 7.7.x**: поиск ингредиента по uid шёл через внутренний метод JEI с другой сигнатурой в 7.7 → ошибка при загрузке избранного с газами и т.п. Теперь только публичный API. Проверено с JEI 7.7.1.153 и 7.8.1.1018.
4. **Экран рецептов JEI оставался доступен** через стрелки прогресса в окнах машин и клавиши JEI. Теперь такие запросы открывает EMI.
5. Сервер без EMI (или с другой версией) больше не помечается в списке серверов как несовместимый.
6. Мелочи: мерцание слотов в тултипах JEI-рецептов, тени и полоса прокрутки в экране настроек, утечки состояния рендера и потоков при ошибках, ожидание конца запуска JEI перед чтением его данных.

## Несовместимость

* **Roughly Enough Items (REI)**: в REI 6.x для Forge встроена заглушка JEI (`modId = "jei"`), поэтому REI нельзя ставить вместе с JEI, а JEI-плагины модов уходят в REI. При EMI + REI оба рисуют список предметов поверх друг друга. Нужен один просмотрщик: **EMI + JEI** или **только REI**. EMI в этом случае пишет в лог предупреждение и не падает.

## Не проверено

* OptiFine (нельзя скачать автоматически). Rubidium (порт Sodium) проверен.
* Перенос рецептов в сетки AE2/Refined Storage напрямую: тестовый стенд не смог открыть их GUI, механизм тот же, что у проверенного Mekanism.
* Tinkers' Construct и CoFH-моды не запускаются в Loom dev-окружении (проблемы ремаппинга); в продакшене работают.
