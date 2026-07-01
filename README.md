# Karst VPN

<p align="center">
  <img src="https://img.shields.io/github/v/release/elev1e1nSure/karst-vpn?label=release" alt="Release">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://github.com/elev1e1nSure/karst-vpn/actions/workflows/release.yml/badge.svg" alt="Build">
</p>

Простой и спокойный VPN-клиент для Android. Вставляешь ссылку от своего провайдера — приложение само разбирается с настройками и подключает одним нажатием.

<p align="center">
  <img src="./karst_latest.png" alt="Скриншот Karst VPN" width="320" style="border-radius: 24px;">
</p>

## Почему Karst

- **Просто.** Ссылка → сервер → кнопка. Никаких ручных настроек протокола.
- **Гибко.** Одиночные ссылки и подписки с автообновлением списка серверов.
- **Быстро.** Встроенная проверка задержки помогает выбрать лучший сервер.
- **Под контролем.** Выбор, что пускать через VPN: весь трафик, с обходом локальной сети или домашних сервисов.
- **Спокойный интерфейс.** Тёмная и светлая тема, подстройка под системную при первом запуске.

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
