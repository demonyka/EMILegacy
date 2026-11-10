# Публикация на Modrinth и CurseForge

Схема та же, что в AE2 Stuff Reborn: проекты на площадках создаются вручную один раз, после этого каждый релиз на GitHub сам загружает мод на обе площадки через `.github/workflows/publish.yml`.

Токены лежат в локальном `.env` (в git не попадает). ID проектов туда же дописываются после создания.

## 0. Репозиторий на GitHub

```
gh repo create demonyka/EMILegacy --public --source . --remote origin \
  --description "Unofficial port of EMI (item and recipe viewer) to Minecraft 1.16.5 Forge"
git push -u origin master --tags
```

В `mods.toml`, `docs/description.md` и этом файле уже стоит адрес `https://github.com/demonyka/EMILegacy`. Если репозиторий будет называться иначе, адрес надо заменить во всех трёх.

## 1. Проект на Modrinth

На https://modrinth.com/dashboard/projects нажми **Create a project**.

| Поле | Значение |
| --- | --- |
| Name | EMI Legacy |
| URL | `emi-legacy` (свободен на 11.11.2026) |
| Summary | Unofficial port of EMI, the item and recipe viewer, to Minecraft 1.16.5 Forge. Search, favorites, recipe tree and recipe filling; recipes of other mods through JEI. |
| Icon | `docs/icon.png` |
| Description | текст из `docs/description.md` |
| Categories | Utility |
| Client side / Server side | Required / Optional |
| License | MIT |
| Source code | https://github.com/demonyka/EMILegacy |
| Issue tracker | https://github.com/demonyka/EMILegacy/issues |

ID проекта (8 символов) показан в настройках проекта. Он понадобится в шаге 3.

Оригинальный EMI есть на обеих площадках. Название «EMI Legacy», своя иконка с плашкой LEGACY и первая строка описания («unofficial port») нужны, чтобы модерация и игроки не приняли порт за оригинал.

## 2. Проект на CurseForge

На https://authors.curseforge.com/#/projects нажми **Create project**, игра Minecraft, класс Mods.

| Поле | Значение |
| --- | --- |
| Name | EMI Legacy |
| Summary | то же, что на Modrinth |
| Main category | Utility & QoL |
| Additional categories | Map and Information |
| Logo | `docs/icon.png` (нужно не меньше 400×400, у иконки 512×512) |
| Description | `docs/description.md`, редактор переключи на Markdown |
| License | MIT License |
| Source | https://github.com/demonyka/EMILegacy |
| Issues | https://github.com/demonyka/EMILegacy/issues |

В `docs/description.md` ссылки на EMI и JEI ведут на Modrinth. Для CurseForge замени их на
`https://www.curseforge.com/minecraft/mc-mods/emi` и `https://www.curseforge.com/minecraft/mc-mods/jei`.

Модерация CurseForge обычно занимает от нескольких часов до пары дней. Project ID виден справа на странице проекта в блоке About Project.

## 3. Доступ для автопубликации

Допиши ID в `.env`, затем:

```
set -a; . ./.env; set +a
gh variable set MODRINTH_ID --repo demonyka/EMILegacy --body "$MODRINTH_ID"
gh variable set CURSEFORGE_ID --repo demonyka/EMILegacy --body "$CURSEFORGE_ID"
gh secret set MODRINTH_TOKEN --repo demonyka/EMILegacy --body "$MODRINTH_TOKEN"
gh secret set CURSEFORGE_TOKEN --repo demonyka/EMILegacy --body "$CURSEFORGE_TOKEN"
```

Площадка без токена просто пропускается, так что можно выкладывать и на одну из них.

## 4. Релиз

```
awk -v v="1.1.24-legacy.1" 'index($0, "## " v " ") == 1 {f=1; next} /^## /{f=0} f' CHANGELOG.md > /tmp/notes.md
gh release create v1.1.24-legacy.1 --repo demonyka/EMILegacy --title "EMI Legacy 1.1.24-legacy.1" --notes-file /tmp/notes.md \
  "forge-1.16.5/build/libs/emi-1.1.24-legacy.1+1.16.5+forge.jar"
```

Тег должен быть `v` плюс `mod_version` из `forge-1.16.5/gradle.properties`, иначе workflow остановится до сборки.
Workflow **publish** соберёт jar и загрузит его так:

| | Modrinth | CurseForge |
| --- | --- | --- |
| Версия | `1.1.24-legacy.1+1.16.5+forge`, Release | то же |
| Загрузчик и игра | Forge, 1.16.5 | Forge, 1.16.5, Java 8 |
| Changelog | раздел из `CHANGELOG.md` | то же |
| JEI | Optional | Optional |

Если проекты ещё на модерации или ID и токены не заданы, workflow упадёт. Его можно повторить вручную: вкладка Actions, **publish**, Run workflow.

## Следующие версии

1. Поменять `mod_version` в `forge-1.16.5/gradle.properties` и добавить раздел `## <версия> (...)` в `CHANGELOG.md`.
2. Закоммитить, создать тег и опубликовать релиз на GitHub. Остальное сделает workflow.
