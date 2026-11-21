package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.helpers.BaseConfig
import org.fossify.messages.extensions.getDefaultKeyboardHeight
import org.fossify.messages.models.Conversation

@Suppress("TooManyFunctions")
class Config(context: Context) : BaseConfig(context) {
    companion object {
        private const val SELECTED_INBOX_CATEGORY = "selected_inbox_category"
        private const val THREAD_CATEGORY_PREFIX = "thread_category_"
        private const val THREAD_CATEGORY_MANUAL_PREFIX = "thread_category_manual_"
        private const val RECENT_OTP = "recent_otp"
        private const val RECENT_OTP_SENDER = "recent_otp_sender"
        private const val RECENT_OTP_TIME = "recent_otp_time"
        private const val RECENT_OTP_MESSAGE = "recent_otp_message"
        private const val RECENT_OTP_THREAD = "recent_otp_thread"
        private const val RECENT_OTP_DISMISSED = "recent_otp_dismissed"

        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var selectedInboxCategory: String
        get() = prefs.getString(SELECTED_INBOX_CATEGORY, "TRANSACTIONS") ?: "TRANSACTIONS"
        set(value) = prefs.edit().putString(SELECTED_INBOX_CATEGORY, value).apply()

    fun getCategoryForThread(threadId: Long): SmsClassifier.Category? {
        val stored = prefs.getString(THREAD_CATEGORY_PREFIX + threadId, null) ?: return null
        return runCatching { SmsClassifier.Category.valueOf(stored) }.getOrNull()
    }

    fun setCategoryForThread(threadId: Long, category: SmsClassifier.Category) {
        prefs.edit().putString(THREAD_CATEGORY_PREFIX + threadId, category.name).apply()
    }

    fun isManualCategoryForThread(threadId: Long): Boolean =
        prefs.getBoolean(THREAD_CATEGORY_MANUAL_PREFIX + threadId, false)

    fun setManualCategoryForThread(threadId: Long, category: SmsClassifier.Category?) {
        prefs.edit().apply {
            if (category == null) {
                remove(THREAD_CATEGORY_PREFIX + threadId)
                remove(THREAD_CATEGORY_MANUAL_PREFIX + threadId)
            } else {
                putString(THREAD_CATEGORY_PREFIX + threadId, category.name)
                putBoolean(THREAD_CATEGORY_MANUAL_PREFIX + threadId, true)
            }
        }.apply()
    }

    fun pruneThreadCategories(activeThreadIds: Set<Long>) {
        val editor = prefs.edit()
        prefs.all.keys.filter {
            it.startsWith(THREAD_CATEGORY_PREFIX) || it.startsWith(THREAD_CATEGORY_MANUAL_PREFIX)
        }.forEach { key ->
            val threadId = key.substringAfterLast('_').toLongOrNull()
            if (threadId == null || threadId !in activeThreadIds) editor.remove(key)
        }
        editor.apply()
    }

    val recentOtp: String
        get() = prefs.getString(RECENT_OTP, "") ?: ""
    val recentOtpSender: String
        get() = prefs.getString(RECENT_OTP_SENDER, "") ?: ""
    val recentOtpTime: Long
        get() = prefs.getLong(RECENT_OTP_TIME, 0L)
    val recentOtpMessageId: Long
        get() = prefs.getLong(RECENT_OTP_MESSAGE, 0L)
    val recentOtpThreadId: Long
        get() = prefs.getLong(RECENT_OTP_THREAD, 0L)
    var isRecentOtpDismissed: Boolean
        get() = prefs.getBoolean(RECENT_OTP_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(RECENT_OTP_DISMISSED, value).apply()

    fun saveRecentOtp(code: String, sender: String, messageId: Long, threadId: Long) {
        prefs.edit().putString(RECENT_OTP, code).putString(RECENT_OTP_SENDER, sender)
            .putLong(RECENT_OTP_TIME, System.currentTimeMillis()).putLong(RECENT_OTP_MESSAGE, messageId)
            .putLong(RECENT_OTP_THREAD, threadId).putBoolean(RECENT_OTP_DISMISSED, false).apply()
    }

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    val isGroupMessageMmsPreferenceResolved: Boolean
        get() = prefs.contains(SEND_GROUP_MESSAGE_MMS)

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_200_KB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit()
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit().putStringSet(BLOCKED_KEYWORDS, blockedKeywords).apply()

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, false)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit().putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable)
            .apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit()
            .putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath).apply()

    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit()
            .putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived).apply()
}
