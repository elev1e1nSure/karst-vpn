_default:
    @just --list

[dependencies]
shell := ["pwsh", "-NoLogo", "-Command"]

# Build debug APK
dbg:
    ./gradlew assembleDebug

# Build release APK
rel:
    ./gradlew assembleRelease

# Run unit tests
test:
    ./gradlew test

# Clean build outputs
clean:
    ./gradlew clean

# Build sing-box AAR (pass tag, e.g. just lib v1.14.0)
lib tag='v1.13.14':
    {{shell}} "./scripts/build_libbox.ps1 -SingBoxTag {{tag}}"

# Full release pipeline — test, build, sign
all: test rel
