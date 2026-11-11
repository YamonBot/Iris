# Native chat capabilities

The 2026-11-11 inspection used the installed Kakao base APK with SHA256
`90a3a9b08b246478ec8d1ae71d94c1a2558f04a23828bf50212f90c1324c6549`.
This is static evidence for that artifact, not a promise of compatibility with
every future Kakao version or evidence that a new Iris APK was deployed.

## Commands and their meanings

`com.kakao.talk.notification.NotificationActionService.onHandleIntent` handles
three actions after checking the notification referer and database state:

| Action | Observed implementation | Product status |
| --- | --- | --- |
| `REPLY_MESSAGE` | Native text send using RemoteInput, chat ID and optional thread ID | Existing Iris send adapter; an existing thread is not a quoted reply |
| `READ_MESSAGE` | Native chat read operation when `is_chat_thread_notification=false`; a separate branch handles threads | Candidate for an explicit typed action; not yet exposed or runtime-qualified |
| `DELETE_NOTIFICATION_MESSAGE` | Notification cleanup through ChatNotifier | Must never be exposed as message recall or deletion |

The existing Iris `DELETE /chat/logs` removes local log data. It is not a native
message recall. Read SQL remains restricted; native mutations must not use
arbitrary database writes or accept caller-selected components/actions.

## Room names

`activity.chatroom.setting.chatroom.ChatRoomProfileEditActivity.B6` binds the
native room-name field and applies a 50-character limit for ordinary rooms.
Saving calls the room profile ViewModel's `N3`, which starts an in-process
profile update coroutine. This establishes a real native UI feature, but not
an external service action or an Iris command. The similarly named
`activity.setting.chatroom.ChatRoomSettingsActivity` configures global chat
preferences and is not evidence of per-room rename support.

Do not publish an invented rename intent, directly edit Kakao databases or
report a product display alias as a native rename. An extension needs a
qualified bridge to the native update operation, exact-profile checks, current
room access, explicit user intent and independent native name readback.

## Signed room coordinates

Native room IDs are exact nonzero signed 64-bit integers. The durable reply
route now uses the same canonical parser as reads, preserving negative values
and rejecting zero, exponent notation, leading zeros and overflow before
dispatch. Message and thread IDs retain their separate positive domains.
The reply fingerprint includes the signed room coordinate, so an idempotency
key cannot replay a receipt from another room.

The focused cached Kotlin 2.1.10/JUnit 4.13.2 run passed four coordinate tests,
including durable command construction and fingerprint distinction. This is
not full Android compilation, native send execution or APK promotion. Final
acceptance still requires the shared APK build and isolated profile/runtime
qualification before changing a running APK pin.
