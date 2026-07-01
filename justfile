_default:
    @just --list

set shell := ["pwsh", "-NoLogo", "-Command"]

# Build debug APK
dbg:
    ./gradlew.bat assembleDebug

# Build release APK
rel:
    ./gradlew.bat assembleRelease

# Run unit tests
test:
    ./gradlew.bat test

# Clean build outputs
clean:
    ./gradlew.bat clean

# Build sing-box AAR (pass tag, e.g. just lib v1.14.0)
lib tag='v1.13.14':
    ./scripts/build_libbox.ps1 -SingBoxTag {{tag}}

# Full release pipeline — test, build, sign
all: test rel
