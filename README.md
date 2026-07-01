# Karst VPN

<p align="center">
  <img src="https://img.shields.io/github/v/release/elev1e1nSure/karst-vpn?label=release" alt="Release">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://github.com/elev1e1nSure/karst-vpn/actions/workflows/release.yml/badge.svg" alt="Build">
</p>

Karst — приложение для подключения к VLESS-серверам и подпискам на Android. Добавляешь ссылку/подписку, выбираешь сервер из списка и подключаешься. Подписки обновляются автоматически, есть проверка задержки серверов и три режима маршрутизации. Тёмная и светлая тема, при первом запуске подстраивается под системную, далее можно изменить в настройках.

<p align="center">
  <img src="./karst_latest.png" alt="Скриншот Karst VPN" width="320" style="border-radius: 24px;">
</p>

## Установка

APK-файлы доступны в [Releases](https://github.com/elev1e1nSure/karst-vpn/releases) — скачать последнюю версию и установить вручную.

## Для разработчиков

Стек: Kotlin, Jetpack Compose, Room, sing-box.

```powershell
.\gradlew.bat assembleDebug   # debug APK
.\gradlew.bat test            # тесты
```

С [just](https://github.com/casey/just): `just dbg`, `just rel`, `just test`, `just` — список команд.

## Безопасность

Не коммитить: `local.properties`, keystore-файлы, реальные VPN-ссылки/подписки, приватные endpoint-ы и пользовательские логи.
