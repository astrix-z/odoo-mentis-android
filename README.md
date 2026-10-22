# odoo-mentis-android

Нативное Android-приложение (Kotlin, Jetpack Compose) для работы с Odoo 16 `odoo-host.pp.ua`.
Первый этап — модуль «Проекты». Серверная часть живёт в репозитории
[odoo-mentis](https://github.com/astrix-z/odoo-mentis) (приватный).

## Как устроено

- Приложение общается с Odoo так же, как веб-клиент: JSON-RPC (`/web/session/authenticate`,
  `/web/dataset/call_kw/<model>/<method>`) от имени вошедшего пользователя. Поэтому все права
  и record rules (в т.ч. `mentis_task_access`) действуют автоматически, дублировать их в
  приложении не нужно.
- Вход по логину и паролю; пароль не сохраняется, сессию держит cookie `session_id`.
  Поддерживается двухфакторная аутентификация Odoo (TOTP) и флажок «не спрашивать на этом
  устройстве» (cookie `td_id`, 90 дней).
- Если сервер не отдаёт список баз (`list_db = False`), на экране входа появляется поле
  «База данных».
- Язык интерфейса берётся из настроек пользователя в Odoo: `ru_*` → русский, `uk_*` →
  украинский, остальное → английский.
- Телефоны и планшеты: список проектов перестраивается в несколько колонок на широком экране.

Структура `app/src/main/java/ua/pp/odoohost/mentis/`:

| Пакет | Что внутри |
|---|---|
| `data/odoo` | `OdooClient` (JSON-RPC, ошибки Odoo → `OdooException`), cookie-jar, разбор JSON Odoo (`false` = пусто) |
| `data/auth` | вход, 2FA, выход, хранение сессии (DataStore) |
| `data/project` | проекты |
| `ui/*` | экраны Compose и их ViewModel |

## Окружение

- JDK 21, Android SDK в `~/Android/Sdk` (`local.properties` с `sdk.dir` не коммитится).
- Android Studio — по желанию, для UI-превью и отладки. Сборка работает и из терминала.

```sh
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # юнит-тесты (без сервера)
./gradlew installDebug             # установить на подключённый телефон
```

## Разработка против локальной реплики

Debug-сборка (`ua.pp.odoohost.mentis.debug`, ставится рядом с релизной) по умолчанию
подключается к `http://localhost:8069` — это нейтрализованная Docker-реплика из `odoo-mentis`.

- Телефон по USB: `adb reverse tcp:8069 tcp:8069`, адрес `http://localhost:8069`.
- Эмулятор: адрес `http://10.0.2.2:8069`.
- База `mentis_dev`, логин и пароль — в `odoo-mentis/.local/credentials.txt`.

Интеграционный тест против реплики (пропускается, если пароль не задан):

```sh
MENTIS_IT_DB=mentis_dev MENTIS_IT_PASSWORD=... ./gradlew testDebugUnitTest --tests '*LocalOdooIntegrationTest'
```

Release-сборка разрешает только HTTPS и предлагает сервер из `mentis.serverUrl`
(`gradle.properties`).

## Релизы

Ветка `main`, релизы — теги `vX.Y.Z` (`versionName`/`versionCode` в `app/build.gradle.kts`).
На первом этапе — подписанный APK для закрытого тестирования. Ключ подписи и
`keystore.properties` в репозиторий не попадают.
