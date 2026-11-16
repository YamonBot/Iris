# Iris

## 필요 환경
* JDK 17 (JDK 20+ 는 Kotlin/KAPT 툴체인과 맞지 않습니다)
* Android SDK (`local.properties` 의 `sdk.dir` 로 지정)
* 별도 jar 다운로드 불필요 — hidden API 는 전부 `java.lang.reflect` 로 런타임에 얻습니다.

## 빌드
```shell
./gradlew :app:assembleDebug
```
산출물은 `output/Iris-debug.apk` 에 복사됩니다. `libsqlcipher.so` 가 `lib/arm64-v8a/` 와 `lib/x86_64/` 에 함께 들어갑니다.

## 실행 (root 가 이미 잡혀 있어야 합니다)
```shell
# APK 를 장치로
adb push output/Iris-debug.apk /data/local/tmp/Iris.apk

# 기동 — app_process 로 띄우고, CLASSPATH 에 APK 를 넣습니다.
CLASSPATH=/data/local/tmp/Iris.apk app_process / party.qwer.iris.Main
```
`app_process` 로 띄운 프로세스는 `nativeLibraryDir` 를 만들지 못하므로, SQLCipher 라이브러리는 위 명령으로 기동된 뒤에야 `/data/local/tmp/Iris.work/native/` 에 자동 추출됩니다.

## 데몬 동작 조건
* `/data/local/tmp/config.json` — 설정 파일. 없으면 기본값으로 생성되고, 이후 변경은 전부 원자적으로(`.tmp` → rename, `.bak` 백업) 저장됩니다. 파일이 깨져 있어도 백업에서 복구합니다.
* `/data/local/tmp/Iris.work/` — owner-only(0700) 작업 디렉터리. 암호화된 KakaoTalk DB 사본과 `libsqlcipher.so` 추출물이 여기 놓입니다. 원문 DB 는 절대 직접 열지 않습니다.
* `/data/local/tmp/Iris.log` — 로그. 없으면 stdout 으로만 나갑니다.
* KakaoTalk 의 uid 로 Android Keystore `keystore2` 를 직접 두드려 `crypto_user_database` 키를 unwrap 합니다. root 라도 shell uid 로는 실패하므로 반드시 root 컨텍스트(`su 0` / `su root`)에서 실행해야 합니다.

## 검증
`app_process` 로 기동했다면 HTTP 포트(기본 3000)에서 `/process-status` 를 보세요. `db_observing`, `bot_id_resolved`, `sqlcipher.*` 가 한 화면에 나옵니다. `/dashboard` 는 GUI 없이 도는 데몬을 위한 유일한 화면입니다.

## 지연된 발송 확인

`GET /reply/{request_id}?room=정확한_방_ID`는 선택된 프로필의 기존 발송
원장과 현재 카카오 DB를 읽습니다. 같은 프로필의 bearer 인증과 유효한
세션이 필요하며, 다른 방 또는 방/콘텐츠 근거가 없는 과거 원장은404입니다.
새 발송을 큐에 넣거나 원장을 변경하지 않습니다.10초 확인 창 이후 이미지가
커밋된 경우에도 원래 요청의 방·시퀀스 경계·본인 작성 여부·콘텐츠 해시를
대조하여 `kakao_db_committed`와 로컬 메시지 ID를 반환할 수 있습니다.

새 요청은 기존 JSONL 원장에 방 좌표와 텍스트 SHA-256 또는 이미지 SHA-1만
추가합니다. 메시지 원문이나 이미지 바이트를 별도 저장하지 않습니다. 기존
원장은 그대로 읽을 수 있고, 기존 명령을 같은 ID·같은 payload로 재확인하면
이 근거를 보완합니다. 발송이 시도된 요청은 재전송하지 않는 기존 규칙을
유지합니다. 응답의 `success`는 로컬 DB 커밋 여부이며 상대 기기 수신 확인은
아닙니다. 확인되지 않은 상태를 새 요청 ID로 다시 보내지 마세요.
