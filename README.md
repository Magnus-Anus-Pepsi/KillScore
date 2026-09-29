# TacZ Kill Score (Forge 1.20.1)

Всплывающие очки за убийства в стиле старых Call of Duty для мода **TacZ**.

## Как собрать
1. Положи свой `tacz-1_20_1-1_1_8-hotfix.jar` в папку `libs/` и переименуй в **`tacz-1.1.8.jar`**
   (сам jar TacZ в архив не включён — у него лицензия CC BY-NC-ND).
2. Нужна JDK 17 (Gradle сам её скачает через toolchain, если её нет).
3. `./gradlew build` (Windows: `gradlew.bat build`)
4. Готовый мод: `build/libs/killscore-1.0.0.jar` -> в папку `mods` (нужен и на сервере, и на клиенте, вместе с TacZ).

Для запуска из IDE: `./gradlew genIntellijRuns` (или `genEclipseRuns`) и запусти `runClient`.

## Бонусы
| Бонус | Условие |
|---|---|
| Kill | базовые очки (игрок 100 / враждебный моб 50 / прочие 10) |
| First Blood | первое убийство «раунда» |
| Headshot | попадание в голову |
| Longshot / Point Blank | дистанция >= 50 / <= 3 блоков |
| No-Scope | снайперка, выстрел без прицеливания с дистанции >= 8 |
| Quickscope | снайперка, выстрел почти сразу после входа в прицел (<= 16 тиков) |
| 360 No-Scope | снайперка, без прицела, камера повернулась >= 330° за ~1 сек |
| Double / Triple / Multi Kill | убийства в течение 4 сек друг за другом |
| Payback | убил игрока, который убил тебя последним |
| Killstreak | каждые 5 убийств без смерти |

Все числа и условия меняются в `config/killscore-common.toml` (сервер) и `config/killscore-client.toml` (HUD: размер, позиция, звук).
Команда `/killscore reset` (оп) сбрасывает раунд (First Blood, серии, счёт).

## Озвучка
Файлы лежат в `src/main/resources/assets/killscore/sounds/*.ogg`, привязка — в `sounds.json`.
Играет один голос за убийство — самый «крутой» из полученных бонусов. Приоритет (сверху вниз):
First Blood, 360 No-Scope, Killstreak, Multi Kill, Triple, Double, Quickscope, No-Scope, Payback, Longshot, Point Blank, Headshot.
Обычное убийство без бонусов — короткий пинг. Громкость: `voice_volume` в `killscore-client.toml`.
Чтобы заменить звук, положи свой `.ogg` (Vorbis) с тем же именем.
