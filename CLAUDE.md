# CLAUDE.md

Instructions for the AI agent in this repo. Keep this file short and specific: only project context useful in every session.

## Project

Karst VPN — Android app in Kotlin and Jetpack Compose. Imports VLESS links and subscriptions, stores servers locally, builds sing-box config, and brings up VPN via Android `VpnService`.

Key areas:

- `app/src/main/kotlin/karst/vpn/core/` — VPN service, sing-box config, platform bridge.
- `app/src/main/kotlin/karst/vpn/data/` — Room, DataStore, repositories, DAO.
- `app/src/main/kotlin/karst/vpn/link/` — VLESS parser, subscription parser, outbound builder.
- `app/src/main/kotlin/karst/vpn/log/` — in-memory ring buffer for log storage.
- `app/src/main/kotlin/karst/vpn/net/` — subscription fetch, latency probe.
- `app/src/main/kotlin/karst/vpn/ui/` — Compose UI, theme, view model.
- `scripts/build_libbox.ps1` — builds `app/libs/libbox.aar` from sing-box.

## Commands

Use Windows PowerShell.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat assembleRelease
.\scripts\build_libbox.ps1
```

`just` aliases: `just dbg`, `just rel`, `just test`, `just clean`, `just lib <tag>`, `just all`, `just`.

Release signing & CI/CD: signed via env vars `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Push tag `v*` → GitHub Actions builds `karst-vpn-v{version}.apk` and uploads to Release (see `.github/workflows/release.yml`).

## Code

- Follow the existing Kotlin/Compose project style.
- Comments in English only, and only for non-obvious decisions or constraints.
- Do not add secrets, real VPN links, subscriptions, tokens, or private endpoints to the repo.
- Do not touch `app/libs/libbox.aar` unless the task involves building or upgrading sing-box.
- Use existing models, serializers, and repositories for structured data — avoid manual string parsing.
- Keep the UI calm and minimal. Do not add decorative elements without a task.

## Verification

- For changes in `link/` run `.\gradlew.bat test`.
- For Compose UI changes, `.\gradlew.bat assembleDebug` is enough unless the user asked for visual inspection.
- For changes in `core/` or VPN routing, a successful build does **not** prove correctness. Explicitly state that testing on an Android device or emulator is required.

## Git

- One logically complete user request — one commit.
- Commit message format: `type(scope): description`.
- Allowed types: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`, `ci`, `build`.
- Do not roll back others' changes without an explicit request.
