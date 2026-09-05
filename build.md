# Iris build and tests

Use JDK 17 and an Android SDK containing platform 35 and build-tools 35.0.0.
Set `JAVA_HOME` and `ANDROID_HOME` to their installed locations. Android Studio,
an emulator, and the old separately downloaded `android-30.jar` are not needed
by the current Gradle project.

Use the pinned Gradle wrapper:

```sh
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1
./gradlew :app:assembleDebug --no-daemon --max-workers=1
```

Tests are colocated as `*_test.kt` in `app/src/main/java`; the Gradle test
source set includes these files and production compilation excludes them.
Check counts in `app/build/test-results/testDebugUnitTest/TEST-*.xml`;
an unmatched filename can silently omit a test.

The APK is copied into `output/Iris-debug.apk`. Building is not deployment:
use the owning runtime's candidate-validation and promotion path, preserving
its current APK, database and rollback evidence.

2026-09-05 validation used official command-line tools build 15859902 (Mac ARM),
archive SHA256 `835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e`.
Verify downloads at https://developer.android.com/studio. Current tools
deprecate `sdkmanager` in favor of `android sdk`; bootstrap is not app runtime.
