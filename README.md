# Karst VPN

Минималистичный Android VPN-клиент для VLESS-подключений и подписок. Приложение построено на Kotlin, Jetpack Compose и `libbox` от sing-box: добавляешь ссылку, выбираешь сервер, подключаешься одной кнопкой.

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./karst_latest2.png">
    <img
      src="./karst_latest2.png"
      alt="Скриншот главного экрана Karst VPN"
      width="360"
      style="max-width: min(100%, 360px); aspect-ratio: 9 / 16; object-fit: contain; border-radius: 24px;"
    >
  </picture>
</p>

## Что умеет

- Импорт одиночных `vless://` ссылок.
- Импорт подписок по `http://` и `https://`, включая base64-encoded списки.
- Хранение серверов и подписок локально через Room.
- Выбор активного сервера и проверка задержки.
- Подключение через Android `VpnService` и sing-box `tun` inbound.
- Темная тема, уведомления о статусе и экран логов.
- Раздельная маршрутизация локальных, приватных и RU-доменов напрямую.

## Стек

- Kotlin, Coroutines, Serialization.
- Jetpack Compose, Material 3.
- Room, DataStore.
- OkHttp.
- sing-box `libbox.aar`.
- Gradle Kotlin DSL.

## Структура

```text
app/src/main/kotlin/karst/vpn/
  core/    VPN service, sing-box config, platform bridge
  data/    Room, DataStore, repositories, entities, DAO
  link/    VLESS and subscription parsing
  log/     in-memory log buffer
  net/     subscription fetch and latency probe
  ui/      Compose screens, theme, view model
```

## Быстрый старт

Требования:

- Android Studio или Android SDK.
- JDK 17.
- Android SDK 36.
- Android NDK, если нужно пересобрать `libbox.aar`.
- Go, если нужно пересобрать `libbox.aar`.

Сборка debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Запуск тестов:

```powershell
.\gradlew.bat test
```

Пересборка `libbox.aar` из sing-box:

```powershell
.\scripts\build_libbox.ps1
```

Скрипт использует `ANDROID_HOME` или `sdk.dir` из `local.properties`, а NDK берет из `ANDROID_NDK_HOME` либо из установленного SDK.

## Release build

Release-подпись берется из переменных окружения:

```powershell
$env:KEYSTORE_BASE64 = "<base64-keystore>"
$env:KEYSTORE_PASSWORD = "<password>"
$env:KEY_ALIAS = "<alias>"
$env:KEY_PASSWORD = "<password>"
.\gradlew.bat assembleRelease
```

Если `KEYSTORE_BASE64` не задан, release собирается без кастомной signing config.

## Безопасность

Не коммить:

- `local.properties`;
- keystore-файлы;
- реальные VPN-ссылки и подписки;
- приватные домены, токены, endpoint-ы и пользовательские логи.

## Статус

Проект находится в активной разработке. Перед изменениями в VPN-core, парсерах ссылок или маршрутизации проверяй поведение на устройстве или эмуляторе: ошибки в этих слоях обычно проявляются только на реальном Android networking stack.
