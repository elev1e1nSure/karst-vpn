# Karst VPN

<p align="center">
  <img src="https://img.shields.io/github/v/release/elev1e1nSure/karst-vpn?label=release" alt="Release">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/core-sing--box-informational" alt="sing-box">
  <img src="https://github.com/elev1e1nSure/karst-vpn/actions/workflows/release.yml/badge.svg" alt="Build">
</p>

Android-клиент для VLESS-подключений и подписок. Добавляешь ссылку, выбираешь сервер, подключаешься одной кнопкой.

<p align="center">
  <img src="./karst_latest.png" alt="Скриншот Karst VPN" width="320" style="border-radius: 24px;">
</p>

## Возможности

- Импорт `vless://` ссылок и подписок (в т.ч. base64), хранение локально через Room.
- Проверка задержки и выбор активного сервера.
- Подключение через Android `VpnService` и sing-box `tun`.
- Гибкая маршрутизация: полный туннель, обход локальной сети, обход RU-доменов.
- Тёмная/светлая тема (авто по системной на первом запуске), экран логов.

## Стек

Kotlin · Coroutines · Jetpack Compose · Material 3 · Room · DataStore · OkHttp · sing-box (`libbox.aar`)

## Сборка

Нужны JDK 17 и Android SDK 36.

```powershell
.\gradlew.bat assembleDebug   # debug APK
.\gradlew.bat test            # тесты
.\scripts\build_libbox.ps1    # пересборка libbox.aar из sing-box
```

С [just](https://github.com/casey/just): `just dbg`, `just rel`, `just test`, `just lib <tag>`, `just` — список команд.

## Релизы

Пуш тега `v*` собирает подписанный APK и публикует его в GitHub Releases (`karst-vpn-v{version}.apk`). Версия берётся из тега.

## Безопасность

Не коммитить: `local.properties`, keystore-файлы, реальные VPN-ссылки/подписки, приватные endpoint-ы и пользовательские логи.
