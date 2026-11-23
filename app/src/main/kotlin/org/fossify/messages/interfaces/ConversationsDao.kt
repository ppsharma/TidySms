package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationWithSnippetOverride
import org.fossify.messages.models.SpamSenderRule

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages " +
        "ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL " +
        "AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC LIMIT 1) " +
        "as new_snippet, * FROM conversations WHERE archived = 0 AND is_spam = 0 AND phone_number NOT IN (SELECT address FROM spam_sender_rules)")
    fun getNonArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getNonArchived(): List<Conversation> =
        getNonArchivedWithLatestSnippet().map { it.toConversation() }

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages " +
        "ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL " +
        "AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC LIMIT 1) " +
        "as new_snippet, * FROM conversations WHERE archived = 0 AND is_spam = 0 " +
        "AND phone_number NOT IN (SELECT address FROM spam_sender_rules) " +
        "AND is_scheduled = 0 AND (inbox_category = :category OR " +
        "(inbox_category IS NULL AND :category = 'UNCATEGORISED')) " +
        "ORDER BY CASE WHEN thread_id IN (:pinnedThreadIds) THEN 0 ELSE 1 END, " +
        "COALESCE((SELECT MAX(messages.date) FROM messages LEFT OUTER JOIN recycle_bin_messages " +
        "ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL " +
        "AND messages.thread_id = conversations.thread_id), date) DESC, thread_id DESC " +
        "LIMIT :pageSize OFFSET :offset")
    fun getInboxPage(
        category: String,
        pinnedThreadIds: List<Long>,
        pageSize: Int,
        offset: Int
    ): List<ConversationWithSnippetOverride>

    @Query("SELECT COUNT(*) FROM conversations WHERE archived = 0 AND is_spam = 0 " +
        "AND phone_number NOT IN (SELECT address FROM spam_sender_rules) " +
        "AND is_scheduled = 0 AND (inbox_category = :category OR " +
        "(inbox_category IS NULL AND :category = 'UNCATEGORISED'))")
    fun getInboxCategoryCount(category: String): Int

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages " +
        "ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL " +
        "AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC LIMIT 1) " +
        "as new_snippet, * FROM conversations WHERE archived = 1")
    fun getAllArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllArchived(): List<Conversation> =
        getAllArchivedWithLatestSnippet().map { it.toConversation() }

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC LIMIT 1) as new_snippet, * FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id) > 0")
    fun getAllWithMessagesInRecycleBinWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllWithMessagesInRecycleBin(): List<Conversation> =
        getAllWithMessagesInRecycleBinWithLatestSnippet().map { it.toConversation() }

    @Query(
        "SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages " +
            "ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL " +
            "AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC " +
            "LIMIT 1) as new_snippet, * FROM conversations " +
            "WHERE (is_spam = 1 OR phone_number IN (SELECT address FROM spam_sender_rules)) AND archived = 0 ORDER BY date DESC"
    )
    fun getSpamWithLatestSnippet(): List<ConversationWithSnippetOverride>

    @Query("UPDATE conversations SET is_spam = :isSpam WHERE thread_id = :threadId")
    fun setSpam(threadId: Long, isSpam: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addSpamSenderRule(rule: SpamSenderRule): Long

    @Query("DELETE FROM spam_sender_rules WHERE address = :address")
    fun removeSpamSenderRule(address: String)

    @Query("SELECT EXISTS(SELECT 1 FROM spam_sender_rules WHERE address = :address)")
    fun isSpamSender(address: String): Boolean

    @Query("UPDATE conversations SET is_spam = 1, archived = 0 WHERE phone_number = :address AND is_group_conversation = 0")
    fun markConversationsFromSenderSpam(address: String)

    @Query("UPDATE conversations SET is_spam = 0 WHERE phone_number = :address AND is_group_conversation = 0")
    fun unmarkConversationsFromSenderSpam(address: String)

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE read = 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text OR phone_number LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query("UPDATE conversations SET inbox_category = :category, classification_manual = :manual, " +
        "classifier_version = :version, classification_confidence = :confidence " +
        "WHERE thread_id = :threadId")
    fun updateInboxClassification(threadId: Long, category: String?, manual: Boolean, version: Int, confidence: Float)

    @Query("UPDATE conversations SET classifier_version = 0 WHERE thread_id = :threadId AND classification_manual = 0")
    fun markThreadForReclassification(threadId: Long)

    @Query("UPDATE conversations SET inbox_category = :category, classification_manual = 1, " +
        "classifier_version = :version, classification_confidence = 1 WHERE thread_id = :threadId")
    fun applyManualCategory(threadId: Long, category: String, version: Int)

    @Query("UPDATE conversations SET read = 1 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET archived = 1 WHERE thread_id = :threadId")
    fun moveToArchive(threadId: Long)

    @Query("UPDATE conversations SET archived = 0 WHERE thread_id = :threadId")
    fun unarchive(threadId: Long)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadId(threadId: Long)
}
