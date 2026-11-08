# Отчёт о проверке безопасности: Delta Visuals 0.9.7

Это **статическая проверка исходников из этого архива**, сделанная поиском по тексту и чтением кода. Она не доказывает, что мод «полностью безопасен» (см. раздел «Чего эта проверка не доказывает»).

## 1. Что делалось

1. Удалены: `presence-server/server.py`, весь HTTP-код (`HttpClient`, `HttpRequest`, `HttpResponse`, `java.net.*`), сетевая синхронизация друзей, общие метки, код друга и секрет клиента.
2. `Presence` (логотип Delta у игроков) стал локальным: ручной список ников (`/delta user add НИК`), без соединений.
3. Друзья переписаны как полностью локальный список (`friends.json`).
4. Затем по всему дереву запущены поиски, перечисленные ниже (команды можно повторить самому).

## 2. Результаты поиска по `src/main/java` (81 файл `.java`)

| Что искали | Команда (сокращённо) | Результат |
|---|---|---|
| Сетевые API | `grep -rnE "java\.net\|javax\.net\|HttpClient\|HttpURLConnection\|URLConnection\|new URL\(\|URI\.create\|Socket\|WebSocket\|InetAddress\|sendAsync"` | **нет совпадений** |
| Адреса `http(s)://` в коде и ресурсах | `grep -rnoE "https?://..."` | **нет совпадений** (в лицензии шрифта Carlito есть ссылки в тексте лицензии, это не код) |
| Процессы, нативный код, динамическая загрузка | `ProcessBuilder`, `Runtime.getRuntime`, `System.load`, `URLClassLoader`, `defineClass`, `Class.forName`, `MethodHandles`, `Unsafe`, `.exec(` | **нет совпадений** |
| Системные свойства и окружение | `System.getenv`, `System.getProperty`, `user.name`, `getHostName` | **нет совпадений** |
| Сессия, токены, аккаунт | `getSession`, `accessToken`, `Authorization`, `sessionService`, `bearer`, `xuid` | **нет совпадений** |
| Отправка пакетов и чата на сервер | `sendPacket`, `sendChatMessage`, `sendCommand`, `networkHandler.send` | **нет совпадений** (6 вызовов `sendMessage(...)` это локальные сообщения игроку в action bar и чат клиента, на сервер они не уходят) |
| Потоки | `new Thread`, `Executor`, `Timer`, `CompletableFuture` | один: `SoundBank` (поток воспроизведения звука из локального файла) |
| Рефлексия | `getMethod`, `getDeclaredMethod`, `invoke`, `setAccessible` | `Compat`, `IconFont`, `UiFont`: подбор методов `Style` (для шрифтов) и геттеров `GameProfile` / списка игроков; `setAccessible` не используется. Код прочитан целиком |

## 3. Что мод читает и пишет на диске

Везде корень `.minecraft/config/delta_visuals/` (через `FabricLoader.getConfigDir()`), записи за пределами этой папки не найдены:

| Файл | Кто | Содержимое |
|---|---|---|
| `profiles/*.json`, `active.txt` | `ConfigManager` | настройки модулей и профили |
| `friends.json` | `FriendList` | ники и UUID друзей (формат `{"version":2,"friends":[{"name","uuid"}]}`) |
| `delta_users.json` | `Presence` | ники для логотипа Delta |
| `markers.json` | `Markers` | ваши метки (координаты, ключ сервера/измерения), только локально |
| `sounds/custom.wav` и т. п. | `SoundBank` | читается ваш звуковой файл, если вы его положили |

Буфер обмена читается только по вашему действию: вставка Ctrl+V в поле друга, «Mark From Clipboard» / `/delta mark clip`, импорт ключа профиля. Записывается только ключ профиля при экспорте.

## 4. Состав проекта

- Файлов по типам: 81 `.java`, 6 `.json`, 2 `.yml`, 2 `.properties`, 2 `.gradle`, 2 `.png`, 1 `.ttf`, 1 `.txt`, 1 `.md`, `gradlew`, `gradlew.bat`.
- Готовых бинарников, `.jar`, `.dll`, `.so`, `.exe`, скриптов `.sh/.py/.js` **нет**. `gradle-wrapper.jar` в проекте отсутствует: его создаёт CI командой `gradle wrapper`.
- Миксины (7, все клиентские, `required: false`): `HeldItemRenderer`, `PlayerListHud`, `Entity` (имя), `PlayerEntity` (имя), `LivingEntity` (статус тотема), `GameRenderer` (FOV), `EntityRenderer` (анимация предметов). Сетевые классы Minecraft ими не затрагиваются.
- `fabric.mod.json`: только клиентская точка входа, зависимости `fabricloader`, `minecraft 1.21.11`, `java 21`, `fabric-api`. Ссылок и контактов нет.
- Зависимости сборки (`build.gradle`): `fabric-loom 1.14-SNAPSHOT`, Minecraft, yarn, Fabric Loader, Fabric API. Репозитории: `maven.fabricmc.net` и Maven Central (только на этапе сборки).
- CI (`.github/workflows`): `actions/checkout`, `setup-java`, `gradle/actions/setup-gradle`, `upload-artifact`; в `release.yml` ещё `softprops/action-gh-release@v2` с правами на запись в релизы. Это сетевая активность **только на сервере GitHub во время сборки**, не самого мода.

## 5. Остатки старых версий на вашем компьютере

- `config/delta_visuals/presence.json` (адрес сервера из версий до 0.9.7) больше не читается. Его можно удалить вручную; мод его не трогает.
- `friends.json` от 0.9.6 содержал приватный «секрет» для удалённого сервера. При первом запуске 0.9.7 файл **автоматически перезаписывается без секрета и кодов**; ники и UUID сохраняются. Друзья, у которых был только код (без ника), не переносятся: по коду без сервера их не определить.

## 6. Чего эта проверка не доказывает (важно)

- Код **не компилировался и не запускался** в моей среде (нет библиотек Minecraft и доступа к сети), проверялся только текстом и синтаксисом. Готовый `.jar` я не видел.
- Поиск по тексту может пропустить обход (например, имя класса, собранное из строк). Поэтому я отдельно прочитал весь рефлексивный код: он вызывает только методы `Style`, `GameProfile` и геттеры списка игроков.
- Не проверялись: сам Minecraft, Fabric Loader/API, плагин `fabric-loom`, Gradle, GitHub Actions и их зависимости; бинарные ресурсы (`.png`, `.ttf`) проверены только по типу.
- Я ИИ и мог ошибиться или что-то упустить. Не принимайте этот отчёт за сертификат; проверьте независимо (ниже).

## 7. Как проверить самому

1. **Собирайте только из этих исходников** (свой GitHub Actions) и не берите чужие готовые `.jar`.
2. **Поиск в собранном jar:** распакуйте `.jar` и выполните, например,
   `javap -c -p -classpath . dev.deltavisuals.social.FriendList | grep -i "java/net\|http"`, затем то же для остальных классов (`for f in $(find . -name '*.class'); do ... done`). Ожидаемый результат: пусто.
3. **Декомпиляция** (CFR, Recaf, JD-GUI) и поиск строк `java.net`, `HttpClient`, `Socket`, `ProcessBuilder`.
4. **Наблюдение за сетью:** запустите одиночную игру при выключенном интернете, затем с Wireshark или «Монитором ресурсов»: у процесса `javaw` не должно быть соединений, кроме игрового сервера и сервисов Mojang/Microsoft/Fabric.
5. **Проверьте папку** `config/delta_visuals/`: там должны быть только файлы из таблицы в разделе 3.
6. Если нужен жёсткий запрет сети для одиночной игры, правило брандмауэра на `javaw.exe`.

## 8. Что в итоге изменено в этой версии

Удалено: `presence-server/` (целиком), HTTP-часть `social/Presence.java`, сетевая часть `social/FriendList.java`, общие метки в `feature/Markers.java` (возвращён оригинал), настройки и информация о сервере в `feature/Friends.java` и `feature/DeltaUsers.java`.
Добавлено/переписано: локальный `social/FriendList.java`, `feature/Friends.java`, панель «Друзья» в `gui/ClickGuiScreen.java`, `SECURITY-REVIEW.md`.
