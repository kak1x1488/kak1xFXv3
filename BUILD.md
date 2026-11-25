# kak1xFX (Fabric 1.21.11, только клиент)

Команды: `/kak1xFX menu` (или `/kak1xfx menu`), `/kak1xFX test`.

## Сборка
1. Java 21.
2. Самый простой путь: скачай шаблон на https://fabricmc.net/develop/template (Minecraft 1.21.11, Yarn),
   назови мод `kak1xfx`, и замени его папку `src` на `src` из этого архива.
   (build.gradle / gradle.properties из архива тоже можно взять.)
3. Либо открой эту папку в IntelliJ IDEA и выполни `gradle wrapper`, потом `./gradlew build`.
4. Готовый jar: `build/libs/kak1xfx-1.0.0.jar` -> в папку `mods` вместе с Fabric API.

Конфиг: `config/kak1xfx.json`.
