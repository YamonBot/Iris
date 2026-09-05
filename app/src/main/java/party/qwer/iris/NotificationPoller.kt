package party.qwer.iris

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.service.notification.StatusBarNotification
import kotlin.concurrent.thread

class NotificationPoller {
    private val processedNotifications = mutableMapOf<String, Long>()

    fun startPolling() {
        thread(start = true) {
            while (true) {
                try {
                    pollNotifications()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(3000)
            }
        }
    }

    private fun pollNotifications() {
        val sbns = getActiveNotifications()

        val currentActiveKeys = mutableSetOf<String>()

        for (sbn in sbns) {
            if (sbn.packageName != "com.kakao.talk") continue

            val key = sbn.key
            val postTime = sbn.postTime
            currentActiveKeys.add(key)

            val lastProcessedTime = processedNotifications[key]

            if (lastProcessedTime == postTime) {
                continue
            }

            val notification = sbn.notification
            val extras = notification.extras ?: continue

            val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (rawTitle == null && rawText == null) {
                processedNotifications[key] = postTime
                continue
            }

            val subText = extras.getString(Notification.EXTRA_SUB_TEXT)
            val summaryText = extras.getString(Notification.EXTRA_SUMMARY_TEXT)
            val room = notificationRoomName(subText, summaryText, rawTitle)
            if (room == null) {
                processedNotifications[key] = postTime
                continue
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    val senders = messages.orEmpty().mapNotNull { message ->
                        val messageBundle = message as? Bundle
                        val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            messageBundle?.getParcelable("sender_person", Person::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            messageBundle?.getParcelable("sender_person") as? Person
                        }
                        person?.let { it.key.orEmpty() to it.name?.toString().orEmpty() }
                    }
                    // A notification title may name the newest sender or the whole room.
                    // Bind each ID only to the name from that same message's Person.
                    for ((senderId, senderName) in latestSenderNames(senders)) {
                        NamesDB.saveName(senderId, senderName, room)
                    }
                }
            } catch (e: Exception) {
                System.err.println("Iris notification identity parsing failed: ${e.javaClass.simpleName}")
                continue
            }

            processedNotifications[key] = postTime
        }

        processedNotifications.keys.retainAll(currentActiveKeys)
    }

    private fun getActiveNotifications(): Array<StatusBarNotification> {
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "notification") as IBinder

            val stub = Class.forName("android.app.INotificationManager\$Stub")
            val inpm = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)

            val methods = inpm.javaClass.methods

            val userId = try {
                val userHandleClass = Class.forName("android.os.UserHandle")
                userHandleClass.getMethod("myUserId").invoke(null) as Int
            } catch (e: Exception) {
                0
            }

            try {
                val getActiveMethod = methods.find {
                    it.name == "getActiveNotifications" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                }
                if (getActiveMethod != null) {
                    val result = getActiveMethod.invoke(inpm, "com.android.shell")
                    val notifications = extractNotifications(result)
                    if (notifications.isNotEmpty()) return notifications
                }
            } catch (e: Exception) {
                // pass
            }

            try {
                val getAppActiveMethod = methods.find {
                    it.name == "getAppActiveNotifications" && it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
                }
                if (getAppActiveMethod != null) {
                    val result = getAppActiveMethod.invoke(inpm, "com.kakao.talk", userId)
                    val notifications = extractNotifications(result)
                    if (notifications.isNotEmpty()) return notifications
                }
            } catch (e: Exception) {
                // pass
            }

            try {
                val getActiveMethod = methods.find {
                    it.name == "getActiveNotifications" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                }
                if (getActiveMethod != null) {
                    val result = getActiveMethod.invoke(inpm, "com.kakao.talk")
                    val notifications = extractNotifications(result)
                    if (notifications.isNotEmpty()) return notifications
                }
            } catch (e: Exception) {
                // pass
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyArray()
    }

    private fun extractNotifications(result: Any?): Array<StatusBarNotification> {
        if (result == null) return emptyArray()

        if (result is Array<*>) {
            return result.filterIsInstance<StatusBarNotification>().toTypedArray()
        }

        try {
            val getListMethod = result.javaClass.getMethod("getList")
            val list = getListMethod.invoke(result) as? List<*>
            if (list != null) {
                return list.filterIsInstance<StatusBarNotification>().toTypedArray()
            }
        } catch (e: Exception) {
            // pass
        }

        return emptyArray()
    }
}
