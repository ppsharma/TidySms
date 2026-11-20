package org.fossify.messages.activities

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.res.ResourcesCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.adapters.ContactsAdapter
import org.fossify.messages.databinding.ActivityConversationDetailsBinding
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getContactFromAddress
import org.fossify.messages.extensions.getThreadParticipants
import org.fossify.messages.extensions.isSavedContactNumber
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.startContactDetailsIntent
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.models.Conversation

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.conversationDetailsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.conversationDetailsNestedScrollview,
            topAppBar = binding.conversationDetailsAppbar,
        )

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                setupTextViews()
                setupSenderCategory()
                setupParticipants()
                setupCustomNotifications()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conversationDetailsAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.conversationDetailsHolder)

        val primaryColor = getProperPrimaryColor()
        arrayOf(
            binding.notificationsHeading,
            binding.conversationNameHeading,
            binding.membersHeading,
            binding.senderCategoryHeading
        ).forEach {
            it.setTextColor(primaryColor)
        }
    }

    private fun setupCustomNotifications() {
        binding.apply {
            notificationsHeading.beVisible()
            customNotificationsHolder.beVisible()
            customNotifications.isChecked = config.customNotifications.contains(threadId.toString())
            customNotificationsButton.beVisibleIf(customNotifications.isChecked)

            customNotificationsHolder.setOnClickListener {
                customNotifications.toggle()
                if (customNotifications.isChecked) {
                    customNotificationsButton.beVisible()
                    config.addCustomNotificationsByThreadId(threadId)
                    createNotificationChannel()
                } else {
                    customNotificationsButton.beGone()
                    config.removeCustomNotificationsByThreadId(threadId)
                    removeNotificationChannel()
                }
            }

            customNotificationsButton.setOnClickListener {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, threadId.toString())
                    startActivity(this)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = conversation?.title
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes
            )
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun removeNotificationChannel() {
        notificationManager.deleteNotificationChannel(threadId.toString())
    }

    private fun setupTextViews() {
        binding.conversationName.apply {
            ResourcesCompat.getDrawable(
                resources,
                org.fossify.commons.R.drawable.ic_edit_vector,
                theme
            )?.apply {
                applyColorFilter(getProperTextColor())
                setCompoundDrawablesWithIntrinsicBounds(null, null, this, null)
            }

            text = conversation?.title
            setOnClickListener {
                RenameConversationDialog(
                    this@ConversationDetailsActivity,
                    conversation!!
                ) { title ->
                    text = title
                    ensureBackgroundThread {
                        conversation = renameConversation(conversation!!, newTitle = title)
                    }
                }
            }
        }
    }


    @Suppress("MaxLineLength")
    private fun setupSenderCategory() {
        val currentConversation = conversation ?: return
        val sender = currentConversation.phoneNumber
        if (sender.isBlank() || currentConversation.isGroupConversation) {
            binding.senderCategoryHeading.beGone()
            binding.senderCategory.beGone()
            return
        }

        fun categoryLabel(category: org.fossify.messages.helpers.SmsClassifier.Category?): String =
            when (category) {
                org.fossify.messages.helpers.SmsClassifier.Category.TRANSACTIONS -> getString(R.string.category_transactions)
                org.fossify.messages.helpers.SmsClassifier.Category.PROMOTIONS -> getString(R.string.category_promotions)
                org.fossify.messages.helpers.SmsClassifier.Category.PERSONAL -> getString(R.string.category_personal)
                org.fossify.messages.helpers.SmsClassifier.Category.UNCATEGORISED -> getString(R.string.category_uncategorised)
                null -> getString(R.string.category_automatic)
            }

        fun refreshCategoryLabel() {
            val manual = config.getManualCategoryForSender(sender)
            val effective = manual ?: org.fossify.messages.helpers.SmsClassifier
                .classify(currentConversation.snippet, sender, isSavedContactNumber(sender)).category
            binding.senderCategory.text = getString(
                R.string.sender_category_value,
                categoryLabel(effective),
                if (manual == null) getString(R.string.category_automatic) else getString(R.string.category_manual)
            )
        }

        refreshCategoryLabel()
        binding.senderCategory.setOnClickListener {
            val categories = arrayOf(
                getString(R.string.category_automatic),
                getString(R.string.category_transactions),
                getString(R.string.category_promotions),
                getString(R.string.category_personal),
                getString(R.string.category_uncategorised)
            )
            val values = arrayOf(
                null,
                org.fossify.messages.helpers.SmsClassifier.Category.TRANSACTIONS,
                org.fossify.messages.helpers.SmsClassifier.Category.PROMOTIONS,
                org.fossify.messages.helpers.SmsClassifier.Category.PERSONAL,
                org.fossify.messages.helpers.SmsClassifier.Category.UNCATEGORISED
            )
            AlertDialog.Builder(this)
                .setTitle(R.string.sender_category)
                .setItems(categories) { _, which ->
                    config.setManualCategoryForSender(sender, values[which])
                    refreshCategoryLabel()
                }
                .show()
        }
    }

    private fun setupParticipants() {
        val adapter = ContactsAdapter(this, participants, binding.participantsRecyclerview) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        binding.participantsRecyclerview.adapter = adapter
    }
}
