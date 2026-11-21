package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations", indices = [Index(value = ["thread_id"], unique = true), Index(value = ["archived", "is_scheduled", "inbox_category", "date"])])
data class Conversation(
    @PrimaryKey @ColumnInfo(name = "thread_id") var threadId: Long,
    @ColumnInfo(name = "snippet") var snippet: String,
    @ColumnInfo(name = "date") var date: Int,
    @ColumnInfo(name = "read") var read: Boolean,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "photo_uri") var photoUri: String,
    @ColumnInfo(name = "is_group_conversation") var isGroupConversation: Boolean,
    @ColumnInfo(name = "phone_number") var phoneNumber: String,
    @ColumnInfo(name = "is_scheduled") var isScheduled: Boolean = false,
    @ColumnInfo(name = "uses_custom_title") var usesCustomTitle: Boolean = false,
    @ColumnInfo(name = "archived") var isArchived: Boolean = false,
    @ColumnInfo(name = "unread_count") var unreadCount: Int = 0,
    @ColumnInfo(name = "inbox_category") var inboxCategory: String? = null,
    @ColumnInfo(name = "classification_manual", defaultValue = "0") var classificationManual: Boolean = false,
    @ColumnInfo(name = "classifier_version", defaultValue = "0") var classifierVersion: Int = 0,
    @ColumnInfo(name = "classification_confidence", defaultValue = "0") var classificationConfidence: Float = 0f,
) {
    companion object {
        fun areItemsTheSame(old: Conversation, new: Conversation): Boolean =
            old.threadId == new.threadId

        fun areContentsTheSame(old: Conversation, new: Conversation): Boolean =
            old.snippet == new.snippet &&
                old.date == new.date &&
                old.read == new.read &&
                old.title == new.title &&
                old.photoUri == new.photoUri &&
                old.isGroupConversation == new.isGroupConversation &&
                old.phoneNumber == new.phoneNumber &&
                old.unreadCount == new.unreadCount &&
                old.inboxCategory == new.inboxCategory &&
                old.classificationManual == new.classificationManual &&
                old.classifierVersion == new.classifierVersion &&
                old.classificationConfidence == new.classificationConfidence
    }
}
