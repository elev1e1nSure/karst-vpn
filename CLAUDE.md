# CLAUDE.md

Инструкции для AI-агента в этом репозитории. Файл должен оставаться коротким и конкретным: сюда попадает только проектный контекст, который полезен в каждой сессии.

## Проект

Karst VPN — Android-приложение на Kotlin и Jetpack Compose. Оно импортирует VLESS-ссылки и подписки, хранит серверы локально, строит sing-box config и поднимает VPN через Android `VpnService`.

Основные зоны:

- `app/src/main/kotlin/karst/vpn/core/` — VPN service, sing-box config, platform bridge.
- `app/src/main/kotlin/karst/vpn/data/` — Room, DataStore, repositories, DAO.
- `app/src/main/kotlin/karst/vpn/link/` — VLESS parser, subscription parser, outbound builder.
- `app/src/main/kotlin/karst/vpn/net/` — subscription fetch, latency probe.
- `app/src/main/kotlin/karst/vpn/ui/` — Compose UI, theme, view model.
- `scripts/build_libbox.ps1` — сборка `app/libs/libbox.aar` из sing-box.

## Команды

Используй Windows PowerShell.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat assembleRelease
.\scripts\build_libbox.ps1
```

Доступен `just` с алиасами: `just dbg`, `just rel`, `just test`, `just clean`, `just lib <tag>`, `just all`, `just`.

Release signing и CI/CD: подпись через env `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Пуш тега `v*` → GitHub Actions собирает `karst-vpn-v{version}.apk` и выгружает в Release (см. `.github/workflows/release.yml`).

## Код

- Следуй текущему Kotlin/Compose стилю проекта.
- Комментарии только на английском и только для неочевидных решений или ограничений.
- Не добавляй секреты, реальные VPN-ссылки, подписки, токены или приватные endpoint-ы в репозиторий.
- Не трогай `app/libs/libbox.aar`, если задача не связана со сборкой или обновлением sing-box.
- Для structured data используй существующие модели, serializers и repositories, а не ручной string parsing.
- Для UI сохраняй текущий спокойный, минималистичный стиль. Не добавляй декоративные элементы без задачи.

## Проверка

- Для изменений в `link/` запускай `.\gradlew.bat test`.
- Для изменений в Compose UI достаточно сборки `.\gradlew.bat assembleDebug`, если пользователь не попросил визуальную проверку.
- Для изменений в `core/` или VPN routing сборка не доказывает корректность. Отдельно укажи, что нужна проверка на Android-устройстве или эмуляторе.

## Git

- Один логически завершенный пользовательский запрос — один commit.
- Формат commit message: `type(scope): description`.
- Разрешенные type: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`, `ci`, `build`.
- Не откатывай чужие изменения без явной просьбы.
