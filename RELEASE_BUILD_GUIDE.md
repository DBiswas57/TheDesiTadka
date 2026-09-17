# RELEASE BUILD GUIDE: TheDesiTadka

================================================================================
PROJECT: TheDesiTadka Android Application
TARGET: Official Clean-Checkout Source Build & Release Procedure
JAVA VERSION: OpenJDK 21
ANDROID SDK: compileSdk 35, minSdk 26, targetSdk 35
================================================================================

## 1. Prerequisites

1. **JDK**: OpenJDK 21 (LTS). Ensure `JAVA_HOME` points to Java 21.
2. **Android SDK**: Android 15 (API 35) SDK platform and build-tools installed.
3. **Environment**: Windows (PowerShell/CMD) or Linux/macOS bash.

---

## 2. Clean Checkout & Compilation Commands

Execute from the `Mobile/` directory:

### Step 1: Clean Project
```bash
# Windows
.\gradlew.bat clean

# Linux / macOS
./gradlew clean
```

### Step 2: Run All Module Unit Tests
```bash
# Windows
.\gradlew.bat test

# Linux / macOS
./gradlew test
```
*Expected Result*: All tests in `:core-model`, `:core-security`, `:provider-engine`, and `:app` pass with `BUILD SUCCESSFUL` (Exit Code 0).

### Step 3: Build Production Release APK
```bash
# Windows
.\gradlew.bat assembleRelease

# Linux / macOS
./gradlew assembleRelease
```
*Build Actions Performed*:
- R8 full-mode bytecode optimization and minification.
- ProGuard rule validation (`proguard-rules.pro`).
- Resource shrinking (`isShrinkResources = true`).
- Lint vital reporting.
- Production APK packaging.

### Step 4: Build Debug APK (Optional for Testing)
```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

---

## 3. Official Release Artifacts

After a successful build:

| Artifact Type | Location | Purpose |
|---|---|---|
| **Release APK (Default)** | `Mobile/app/build/outputs/apk/release/app-release.apk` | Official production release bundle |
| **Release Archive** | `release/TheDesiTadka-v1.0.0-release.apk` | Mirrored tagged release binary |
| **Debug APK** | `Mobile/app/build/outputs/apk/debug/app-debug.apk` | Local developer testing binary |

### Release APK Verification (PowerShell)
```powershell
Get-FileHash -Algorithm SHA256 .\Mobile\app\build\outputs\apk\release\app-release.apk
```
*Verified SHA-256 Digest*:
`0B2D54130F8499DD457626EEA86B1582C024772F47089C763B6F7A8D45DB85CA`
*Size*: 6,072,798 bytes (~5.79 MB)

---

## 4. Production Keystore Configuration

To sign with a custom production keystore without checking secrets into git:

In `~/.gradle/gradle.properties` (user home directory, not in repo):
```properties
THEDESITADKA_RELEASE_STORE_FILE=/path/to/thedesitadka-release.jks
THEDESITADKA_RELEASE_STORE_PASSWORD=your_keystore_password
THEDESITADKA_RELEASE_KEY_ALIAS=thedesitadka_key
THEDESITADKA_RELEASE_KEY_PASSWORD=your_key_password
```

And configure `Mobile/app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file(findProperty("THEDESITADKA_RELEASE_STORE_FILE") ?: "debug.keystore")
        storePassword = findProperty("THEDESITADKA_RELEASE_STORE_PASSWORD") as String?
        keyAlias = findProperty("THEDESITADKA_RELEASE_KEY_ALIAS") as String?
        keyPassword = findProperty("THEDESITADKA_RELEASE_KEY_PASSWORD") as String?
    }
}
```

---

## 5. Installing to a Real Device

```bash
adb install -r release/TheDesiTadka-v1.0.0-release.apk
```
