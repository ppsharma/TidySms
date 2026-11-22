package org.fossify.messages.workers

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.messages.R
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.isSavedContactNumber
import org.fossify.messages.helpers.SmsClassifier

/**
 * Resumable, on-device inbox initialization. Each run processes a bounded batch;
 * classifications are committed per thread so a retry never repeats completed work.
 */
class InboxClassificationWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val config = applicationContext.config

        // A completed pass followed by a newly requested pass is a new progress
        // session. An interrupted/retried pass must retain its durable progress.
        if (config.inboxInitializationComplete) {
            config.inboxInitializationProcessed = 0
            config.inboxInitializationTotal = 0
        }
        config.inboxInitializationComplete = false

        val database = applicationContext.conversationsDB
        val conversations = database.getNonArchived().filter { !it.isScheduled }
        val total = conversations.size

        // Migrate old SharedPreferences classifications before making any automatic choice.
        conversations.forEach { conversation ->
            if (conversation.inboxCategory == null) {
                val oldCategory = config.getCategoryForThread(conversation.threadId)
                if (oldCategory != null) {
                    val manual = config.isManualCategoryForThread(conversation.threadId)
                    database.updateInboxClassification(
                        conversation.threadId, oldCategory.name, manual,
                        SmsClassifier.CLASSIFIER_VERSION, 1.0f
                    )
                    conversation.inboxCategory = oldCategory.name
                    conversation.classificationManual = manual
                    conversation.classifierVersion = SmsClassifier.CLASSIFIER_VERSION
                }
            }
        }

        val pending = conversations.filter {
            !it.classificationManual &&
                (it.inboxCategory == null || it.classifierVersion < SmsClassifier.CLASSIFIER_VERSION)
        }
        val batch = pending.take(MAX_THREADS_PER_RUN)

        // Room is the source of truth. Retain a high-water mark so a transiently
        // stale snapshot or retry cannot make the displayed progress move backwards.
        val processedBefore = maxOf(
            config.inboxInitializationProcessed,
            total - pending.size
        ).coerceAtMost(total)
        val progressTotal = maxOf(total, processedBefore)
        config.inboxInitializationTotal = progressTotal
        config.inboxInitializationProcessed = processedBefore
        publishProgress(processedBefore, progressTotal)

        batch.forEachIndexed { index, conversation ->
            if (isStopped) return Result.retry()

            val sample = runCatching {
                applicationContext.getMessages(
                    threadId = conversation.threadId,
                    includeScheduledMessages = false,
                    limit = RECENT_MESSAGES_PER_THREAD
                )
            }.getOrDefault(arrayListOf())
            val messages = sample.filter { it.isReceivedMessage() }.map { it.body }
                .ifEmpty { sample.map { it.body } }
            val result = SmsClassifier.classifyThread(
                messages = messages,
                sender = conversation.phoneNumber,
                isSavedContact = applicationContext.isSavedContactNumber(conversation.phoneNumber)
            )
            database.updateInboxClassification(
                conversation.threadId,
                result.category.name,
                false,
                SmsClassifier.CLASSIFIER_VERSION,
                result.confidence
            )

            val calculated = processedBefore + index + 1
            val processed = maxOf(config.inboxInitializationProcessed, calculated)
                .coerceAtMost(progressTotal)
            if ((index + 1) % PROGRESS_UPDATE_INTERVAL == 0 || index == batch.lastIndex) {
                config.inboxInitializationProcessed = processed
                publishProgress(processed, progressTotal)
            }
        }

        return if (pending.size > batch.size) {
            // WorkManager retries this worker. The next execution resumes from Room
            // and the persisted high-water mark; it must not publish a zero reset.
            Result.retry()
        } else {
            config.inboxInitializationProcessed = progressTotal
            config.inboxInitializationTotal = progressTotal
            config.inboxInitializationComplete = true
            postInitializationNotification(progressTotal, progressTotal, completed = true)
            Result.success()
        }
    }

    private fun publishProgress(processed: Int, total: Int) {
        setProgressAsync(progressData(processed, total))
        postInitializationNotification(processed, total, completed = false)
    }

    private fun postInitializationNotification(processed: Int, total: Int, completed: Boolean) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    INITIALIZATION_CHANNEL,
                    applicationContext.getString(R.string.inbox_initialization_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                INITIALIZATION_NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val title = applicationContext.getString(
            if (completed) R.string.inbox_initialization_complete else R.string.inbox_initialization_title
        )
        val text = if (completed) {
            applicationContext.getString(R.string.inbox_initialization_done, total)
        } else if (total > 0) {
            applicationContext.getString(R.string.organizing_conversations_progress, processed, total)
        } else {
            applicationContext.getString(R.string.organizing_conversations)
        }
        val notification = NotificationCompat.Builder(applicationContext, INITIALIZATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_messenger)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(!completed)
            .setProgress(total.coerceAtLeast(0), processed.coerceAtLeast(0), total <= 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setAutoCancel(completed)
            .build()
        manager.notify(INITIALIZATION_NOTIFICATION_ID, notification)
    }

    private fun progressData(processed: Int, total: Int) = Data.Builder()
        .putInt(KEY_PROCESSED, processed)
        .putInt(KEY_TOTAL, total)
        .build()

    companion object {
        const val UNIQUE_WORK_NAME = "tidysms_inbox_initialization"
        const val KEY_PROCESSED = "processed_threads"
        const val KEY_TOTAL = "total_threads"
        private const val INITIALIZATION_CHANNEL = "tidysms_inbox_initialization"
        private const val INITIALIZATION_NOTIFICATION_ID = 72041
        private const val MAX_THREADS_PER_RUN = 100
        private const val RECENT_MESSAGES_PER_THREAD = 7
        private const val PROGRESS_UPDATE_INTERVAL = 10
    }
}
