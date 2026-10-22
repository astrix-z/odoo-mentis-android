# CLAUDE.md

Native Android client (Kotlin, Jetpack Compose) for the Odoo 16 instance `odoo-host.pp.ua`. `README.md` (Russian) is the authoritative doc.

## Key facts

- **Public repository.** Never commit credentials, `local.properties`, signing keys, production data or anything from `odoo-mentis/.local/`.
- The server side is the private repo `../odoo-mentis` (Odoo source, custom addons such as `mentis_task_access`, deployment). Anything the app needs from the server (new endpoints, push notifications) goes there as an addon under `addons/` and follows that repo's deployment rules.
- The app uses Odoo's own web JSON-RPC as the logged-in user (`OdooClient.callKw`), so record rules apply. Don't add client-side access filtering. Odoo returns `false` for empty fields of any type; use the helpers in `data/odoo/OdooJson.kt`.
- AGP 9 with built-in Kotlin: don't apply `org.jetbrains.kotlin.android` in modules (the root declaration only pins the Kotlin version). compileSdk 37 (required by current AndroidX), targetSdk 36, minSdk 26.
- UI strings live in `values/` (en), `values-ru/`, `values-uk/`. Keep all three in sync.
- Hand-wired dependencies in `AppContainer`. No DI framework.

## Commands

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests 'ua.pp.odoohost.mentis.data.auth.AuthRepositoryTest'
```

Local server: the Docker replica from `../odoo-mentis` (`docker compose up -d` there) at `http://localhost:8069`, DB `mentis_dev`. Phone: `adb reverse tcp:8069 tcp:8069`. `LocalOdooIntegrationTest` runs against it when `MENTIS_IT_PASSWORD` is set.

## Branches and releases

`main` only. Releases are tags `vX.Y.Z` matching `versionName`.
