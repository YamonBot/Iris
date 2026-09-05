# Iris build and tests

Late reply reconciliation is implemented in our gateway `features/reply`
extension, not upstream Kakao transmission code. Repeating the same request and
payload for a `KAKAO_DB_UNCONFIRMED` record reuses its original baseline and probes
the database without sending again. Missing evidence remains unconfirmed;
fingerprint conflicts remain rejected. Unit recovery coverage spans both
PROCESSING/UNCONFIRMED, present/missing baselines, and present/missing rows.
Runtime promotion must preserve the existing ledger and prove reconciliation
against the original request; unit success is not an Android deployment receipt.

Candidate staging (2026-09-06): source
`0b10024ba742e4669d62aedae597bbc32ee5f338`, JDK 17, pinned Gradle unit tests and
assembleDebug succeeded. APK SHA256:
`55503710a7f50e63549d8db9c2af3e740dbb7d6c5cf89f1da4d34701b050566f`.
The same hash was verified on pve0 in its existing digest-addressed
`/var/lib/kakao-gateway-runtime/iris-development/` staging directory.
Production remained active on rc.4 hash
`17c31b047813fae07a006fe7499268c8174c80a11860617cd5b46ced88429e13`, CT112
running. No service switch, ledger mutation, or message send occurred in staging.

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
