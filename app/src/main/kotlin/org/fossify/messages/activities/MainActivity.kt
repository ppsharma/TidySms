package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AlertDialog
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import org.fossify.messages.workers.InboxClassificationWorker
import android.provider.Telephony
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.isSavedContactNumber
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.SmsClassifier
import org.fossify.messages.helpers.MESSAGE_ID
import org.fossify.messages.helpers.IS_MMS
import org.fossify.messages.receivers.DeleteSmsReceiver
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@Suppress("LargeClass")
class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = false
    
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private var selectedCategory: SmsClassifier.Category? = SmsClassifier.Category.TRANSACTIONS
    private var latestConversations = arrayListOf<Conversation>()
    private var hasLoadedConversations = false
    private var lastRenderedManualCategoryState: List<Pair<Long, String?>> = emptyList()
    private var inboxCategoryControlsInitialized = false
    private var inboxInitializationObserverAttached = false
    @Volatile private var isClassifyingThreads = false
    private val inboxPageSize = 50
    private var inboxPageOffset = 0
    private var inboxHasMorePages = true
    private var inboxPageLoading = false
    private var inboxPageGeneration = 0
    private var displayedInboxConversations = arrayListOf<Conversation>()

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }

        if (checkAppSideloading()) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        refreshMenuItems()

        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        updateInboxCategoryStyles()
        updateRecentOtpCard()
        val currentManualCategoryState = latestConversations.map { conversation ->
            conversation.threadId to config.getCategoryForThread(conversation.threadId)?.name
        }
        if (currentManualCategoryState != lastRenderedManualCategoryState) {
            showFilteredConversations()
        }
        if (!config.inboxInitializationComplete && latestConversations.any { it.inboxCategory == null }) {
            classifyUnclassifiedThreads(latestConversations)
        }
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            appLockManager.lock()
            false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.toggleHideOnScroll(true)
        binding.mainMenu.setupMenu()
        binding.mainMenu.requireToolbar().title = getString(R.string.messages_title)

        binding.mainMenu.onSearchClosedListener = {
            fadeOutSearch()
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            if (text.isNotEmpty()) {
                if (binding.searchHolder.alpha < 1f) {
                    binding.searchHolder.fadeIn()
                }
            } else {
                fadeOutSearch()
            }
            searchTextChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.show_recycle_bin -> launchRecycleBin()
                R.id.show_archived -> launchArchivedConversations()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        if (!inboxCategoryControlsInitialized) {
            setupInboxCategoryControls()
            binding.noConversationsPlaceholder2.setOnClickListener {
                launchNewConversation()
            }
            binding.conversationsFab.setOnClickListener {
                launchNewConversation()
            }
            inboxCategoryControlsInitialized = true
        }
        getCachedConversations()
    }

    private fun setupInboxCategoryControls() {
        selectedCategory = runCatching {
            SmsClassifier.Category.valueOf(config.selectedInboxCategory)
        }.getOrDefault(SmsClassifier.Category.TRANSACTIONS)
        binding.inboxCategoryGroup.check(categoryViewId(selectedCategory!!))
        binding.inboxCategoryGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedCategory = categoryForViewId(checkedId) ?: SmsClassifier.Category.TRANSACTIONS
            config.selectedInboxCategory = selectedCategory!!.name
            updateInboxCategoryStyles()
            updateRecentOtpCard()
            showFilteredConversations()
        }
        updateInboxCategoryStyles()
        observeInboxInitialization()
    }

    private fun observeInboxInitialization() {
        if (inboxInitializationObserverAttached) return
        inboxInitializationObserverAttached = true
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(InboxClassificationWorker.UNIQUE_WORK_NAME)
            .observe(this) { workInfos ->
                val info = workInfos.firstOrNull()
                if (info == null) {
                    binding.conversationsProgressBar.hide()
                    return@observe
                }
                val active = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
                if (active) {
                    binding.conversationsProgressBar.show()
                    if (displayedInboxConversations.isEmpty()) {
                        val processed = info.progress.getInt(InboxClassificationWorker.KEY_PROCESSED, config.inboxInitializationProcessed)
                        val total = info.progress.getInt(InboxClassificationWorker.KEY_TOTAL, config.inboxInitializationTotal)
                        binding.noConversationsPlaceholder.beVisible()
                        binding.noConversationsPlaceholder.text = if (total > 0) {
                            getString(R.string.organizing_conversations_progress, processed, total)
                        } else {
                            getString(R.string.organizing_conversations)
                        }
                    }
                } else {
                    binding.conversationsProgressBar.hide()
                    if (info.state == WorkInfo.State.SUCCEEDED) {
                        showFilteredConversations()
                    } else if (displayedInboxConversations.isEmpty() && latestConversations.isNotEmpty()) {
                        showOrHidePlaceholder(true)
                    }
                }
            }
    }

    private fun categoryViewId(category: SmsClassifier.Category) = when (category) {
        SmsClassifier.Category.TRANSACTIONS -> R.id.category_transactions
        SmsClassifier.Category.SERVICES -> R.id.category_services
        SmsClassifier.Category.PROMOTIONS -> R.id.category_promotions
        SmsClassifier.Category.PERSONAL -> R.id.category_personal
        SmsClassifier.Category.UNCATEGORISED -> R.id.category_uncategorised
    }

    private fun categoryForViewId(viewId: Int) = when (viewId) {
        R.id.category_transactions -> SmsClassifier.Category.TRANSACTIONS
        R.id.category_services -> SmsClassifier.Category.SERVICES
        R.id.category_promotions -> SmsClassifier.Category.PROMOTIONS
        R.id.category_personal -> SmsClassifier.Category.PERSONAL
        R.id.category_uncategorised -> SmsClassifier.Category.UNCATEGORISED
        else -> null
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                ArrayList(conversationsDB.getNonArchived())
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    ArrayList(conversations + archived)
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)
            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages
                    // to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    // FIXME: Scheduled message date is being reset here. Conversations with
                    //  scheduled messages will have their original date.
                    insertOrUpdateConversation(conv)
                }
            }

            config.pruneThreadCategories((conversations.map { it.threadId } + cachedConversations.filter { it.isScheduled }.map { it.threadId }).toSet())

            val allConversations = ArrayList(conversationsDB.getNonArchived())
            runOnUiThread {
                setupConversations(allConversations)
            }

            classifyUnclassifiedThreads(allConversations)
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            binding.conversationsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (dy > 0 && layoutManager.findLastVisibleItemPosition() >=
                        displayedInboxConversations.size - 8
                    ) {
                        loadNextInboxPage()
                    }
                }
            })
            binding.conversationsList.itemAnimator = null
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        latestConversations = ArrayList(conversations)
        hasLoadedConversations = true
        if (cached && config.appRunCount == 1) {
            // there are no cached conversations on the first run so we show the
            // loading placeholder and progress until we are done loading from telephony
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        showFilteredConversations(updatePlaceholder = !cached)
        updateRecentOtpCard()
    }


    @Suppress("MagicNumber")
    private fun updateInboxCategoryStyles() {
        val primaryColor = getProperPrimaryColor()
        val textColor = getProperTextColor()
        val backgroundColor = getProperBackgroundColor()
        val density = resources.displayMetrics.density
        val cornerRadius = 18f * density
        for (index in 0 until binding.inboxCategoryGroup.childCount) {
            val button = binding.inboxCategoryGroup.getChildAt(index) as? android.widget.RadioButton ?: continue
            val selected = button.isChecked
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius)
                setColor(if (selected) primaryColor else backgroundColor)
                setStroke(density.toInt().coerceAtLeast(1), if (selected) primaryColor else primaryColor.adjustAlpha(LOWER_ALPHA))
            }
            button.setTextColor(if (selected) Color.WHITE else textColor)
            button.alpha = if (selected) 1f else 0.88f
        }
    }

    private fun showFilteredConversations(updatePlaceholder: Boolean = true) {
        lastRenderedManualCategoryState = latestConversations.map {
            it.threadId to it.inboxCategory
        }
        inboxPageGeneration++
        inboxPageOffset = 0
        inboxHasMorePages = true
        displayedInboxConversations.clear()
        loadInboxPage(reset = true, generation = inboxPageGeneration, updatePlaceholder = updatePlaceholder)
    }

    private fun loadInboxPage(
        reset: Boolean,
        generation: Int = inboxPageGeneration,
        updatePlaceholder: Boolean = true
    ) {
        if (inboxPageLoading || (!reset && !inboxHasMorePages)) return
        inboxPageLoading = true
        val category = (selectedCategory ?: SmsClassifier.Category.TRANSACTIONS).name
        val offset = if (reset) 0 else inboxPageOffset
        val pinnedIds = config.pinnedConversations.mapNotNull { it.toLongOrNull() }
        ensureBackgroundThread {
            val page = conversationsDB.getInboxPage(category, pinnedIds, inboxPageSize, offset)
                .map { it.toConversation() }
            runOnUiThread {
                inboxPageLoading = false
                if (generation != inboxPageGeneration) {
                    loadInboxPage(reset = true, generation = inboxPageGeneration, updatePlaceholder = updatePlaceholder)
                    return@runOnUiThread
                }
                if (reset) displayedInboxConversations.clear()
                displayedInboxConversations.addAll(page)
                inboxPageOffset = offset + page.size
                inboxHasMorePages = page.size == inboxPageSize
                getOrCreateConversationsAdapter().updateConversations(ArrayList(displayedInboxConversations)) {
                    if (updatePlaceholder) showOrHidePlaceholder(displayedInboxConversations.isEmpty())
                }
                if (updatePlaceholder) showOrHidePlaceholder(displayedInboxConversations.isEmpty())
            }
        }
    }

    private fun loadNextInboxPage() {
        if (!inboxPageLoading && inboxHasMorePages) {
            loadInboxPage(reset = false)
        }
    }

    /** Schedule persistent background initialization; WorkManager resumes after process death. */
    private fun classifyUnclassifiedThreads(conversations: List<Conversation>) {
        if (conversations.none {
                !it.isScheduled && (it.inboxCategory == null ||
                    (!it.classificationManual && it.classifierVersion < SmsClassifier.CLASSIFIER_VERSION))
            }
        ) return
        val request = OneTimeWorkRequestBuilder<InboxClassificationWorker>()
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                10,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            InboxClassificationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun updateRecentOtpCard() {
        val card = binding.root.findViewById<android.view.View>(R.id.recent_otp_card) ?: return
        val codeView = binding.root.findViewById<android.widget.TextView>(R.id.recent_otp_code)
        val senderView = binding.root.findViewById<android.widget.TextView>(R.id.recent_otp_sender_time)
        val code = config.recentOtp
        val visible = code.isNotBlank() && !config.isRecentOtpDismissed &&
            selectedCategory == SmsClassifier.Category.TRANSACTIONS
        card.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        if (!visible) return
        val primary = getProperPrimaryColor()
        val surface = getProperBackgroundColor()
        val mix = 0.10f
        val cardColor = Color.rgb(
            (Color.red(surface) * (1f - mix) + Color.red(primary) * mix).toInt(),
            (Color.green(surface) * (1f - mix) + Color.green(primary) * mix).toInt(),
            (Color.blue(surface) * (1f - mix) + Color.blue(primary) * mix).toInt()
        )
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * resources.displayMetrics.density
            setColor(cardColor)
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), primary.adjustAlpha(0.45f))
        }
        codeView.setTextColor(getProperTextColor())
        senderView.setTextColor(getProperTextColor().adjustAlpha(0.78f))
        binding.root.findViewById<android.view.View>(R.id.recent_otp_copy).apply {
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OTP", code))
                toast(R.string.copy_otp)
            }
            (this as? android.widget.TextView)?.setTextColor(primary)
        }
        (binding.root.findViewById<android.view.View>(R.id.recent_otp_dismiss) as? android.widget.TextView)?.setTextColor(getProperTextColor())
        (binding.root.findViewById<android.view.View>(R.id.recent_otp_delete) as? android.widget.TextView)?.setTextColor(getProperTextColor())
        codeView.text = code
        val elapsed = ((System.currentTimeMillis() - config.recentOtpTime).coerceAtLeast(0L) / 60000L)
        val relative = when {
            elapsed == 0L -> "Just now"
            elapsed == 1L -> "1 min ago"
            elapsed < 60L -> "$elapsed mins ago"
            elapsed < 1440L -> "${elapsed / 60} hr ago"
            else -> "${elapsed / 1440} days ago"
        }
        senderView.text = "${config.recentOtpSender} · $relative"
        binding.root.findViewById<android.view.View>(R.id.recent_otp_dismiss).setOnClickListener {
            config.isRecentOtpDismissed = true
            updateRecentOtpCard()
        }
        binding.root.findViewById<android.view.View>(R.id.recent_otp_delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_otp_confirmation)
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .setPositiveButton(org.fossify.commons.R.string.delete) { _, _ ->
                    sendBroadcast(Intent(this, DeleteSmsReceiver::class.java).apply {
                        putExtra(THREAD_ID, config.recentOtpThreadId)
                        putExtra(MESSAGE_ID, config.recentOtpMessageId)
                        putExtra(IS_MMS, false)
                    })
                    config.isRecentOtpDismissed = true
                    updateRecentOtpCard()
                }.show()
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(
            if (latestConversations.isNotEmpty()) {
                R.string.no_conversations_in_category
            } else {
                R.string.no_conversations_found
            }
        )
        // A category can be empty while the rest of the inbox is populated or
        // still being classified. Never invite the user to start a conversation
        // just because the selected category has no matches.
        binding.noConversationsPlaceholder2.beVisibleIf(show && latestConversations.isEmpty())
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, ArchivedConversationsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = R.string.faq_4_title,
                text = R.string.faq_4_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
