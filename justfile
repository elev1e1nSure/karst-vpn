_default:
    @just --list

set shell := ["pwsh", "-NoLogo", "-Command"]

# Build debug APK
[windows]
dbg:
    ./gradlew.bat assembleDebug

[unix]
dbg:
    ./gradlew assembleDebug

# Build release APK
[windows]
rel:
    ./gradlew.bat assembleRelease

[unix]
rel:
    ./gradlew assembleRelease

# Run unit tests
[windows]
test:
    ./gradlew.bat test

[unix]
test:
    ./gradlew test

# Check Kotlin formatting/style
[windows]
lint:
    ./gradlew.bat ktlintCheck

[unix]
lint:
    ./gradlew ktlintCheck

# Auto-format Kotlin sources
[windows]
fmt:
    ./gradlew.bat ktlintFormat

[unix]
fmt:
    ./gradlew ktlintFormat

# Composite check — tests + lint, run locally and in CI
check: test lint

# Clean build outputs
[windows]
clean:
    ./gradlew.bat clean

[unix]
clean:
    ./gradlew clean

# Build debug APK and install via adb
install: dbg
    adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build sing-box AAR (pass tag, e.g. just lib v1.14.0)
lib tag='v1.13.14':
    ./scripts/build_libbox.ps1 -SingBoxTag {{tag}}

# Full release pipeline — test, build, sign
all: test rel
