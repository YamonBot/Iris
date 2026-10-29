# Explicit Android profile isolation

An additional Iris daemon can target an existing Android secondary user or managed
work profile. This is not a security boundary against a root operator of the device.
Do not clone an existing account's app data into the new profile.

Set all three variables before starting the daemon:

| Variable | Required value |
| --- | --- |
| `IRIS_ANDROID_USER_ID` | The secondary Android user ID; not an application UID |
| `KAKAOTALK_APP_UID` | KakaoTalk's full Linux UID in that user, obtained from Android package metadata |
| `IRIS_EXPECTED_BOT_ID` | The verified KakaoTalk account's numeric ID |

The daemon selects `/data/user/<user>/com.kakao.talk`, validates its owning UID,
uses that user for Android service/activity/broadcast dispatch, and selects the
same full UID for the keystore. A missing data directory does not fall back to
the primary user. An unexpected account prevents startup before workers begin.

Configuration and working files live in `/data/local/tmp/iris-users/<user>` with
owner-only permissions. Legacy `IRIS_CONFIG_PATH` and `IRIS_WORK_DIR` overrides
must match these derived locations. The HTTP port is `3000 + user ID`; a port
argument must match. Media staging and cleanup use only the profile's `files/iris`
subdirectory. Without `IRIS_ANDROID_USER_ID`, existing daemon behavior is retained.

Keep the profile endpoint private and apply its own caller authentication. Do not
publish the raw SQL, configuration, or sending endpoints as personal MCP tools.
The shared root daemon is not a multi-tenant security sandbox.

This change has not qualified modern KakaoTalk work-profile login, locked-user
behavior, native sending/media permissions, or account changes during a running
session. Those require device acceptance before production use. Android profiles
share the installed package version, so an APK upgrade requires checking all users.
