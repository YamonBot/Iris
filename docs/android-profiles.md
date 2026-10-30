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


## Authenticated MCP read compatibility

Explicit profiles require a token projection at
`/data/local/tmp/iris-users/<user>/tokens`. Another profile or company token path
is rejected. Missing tokens fail startup before workers are launched. All profile
HTTP and WebSocket routes require that bearer; the current upstream daemon's
legacy unset-profile mode remains unchanged. The profile adapter exposes the
existing `/healthz`, `/identity/<room>/<actor>` and `/media/<message>` read protocol
used by the shared Room API. Media validation and token rotation reuse the
production feature implementations from YamonBot/Iris cf2191d52217a968c26f884a3af463a88348f571.
An unset-profile daemon may explicitly opt into the same authenticated read
protocol with `IRIS_AUTH_TOKEN_FILE`; the default remains unchanged. This supports
isolated compatibility validation without copying one account into another profile.
The latest upstream query/decryption and Android profile paths remain authoritative.
This is read-protocol compatibility; it does not claim the legacy custom durable
reply-receipt protocol or native Kakao authentication has been accepted.

The installed Android package version is shared across managed profiles. Validate
a new signed Kakao APK set in an isolated disposable Android before upgrading the
shared package. Creating or launching a profile does not prove a personal account
is linked. Complete the supported subdevice flow and verify actual source account,
room reads and media before publishing the personal MCP.
