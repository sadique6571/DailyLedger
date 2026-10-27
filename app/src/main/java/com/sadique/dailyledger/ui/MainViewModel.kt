package com.sadique.dailyledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sadique.dailyledger.ai.*
import com.sadique.dailyledger.sync.SyncScheduler
import com.sadique.dailyledger.sync.BackupLock
import kotlinx.coroutines.sync.withLock
import com.sadique.dailyledger.auth.FirebaseRuntime
import kotlin.coroutines.cancellation.CancellationException

data class OfflineProfile(val ownerId: String, val records: Int)

class MainViewModel(app:Application,val ownerId:String):AndroidViewModel(app){
    val repo=LedgerRepository(AppDatabase.get(app),ownerId) { CategoryLearningStore(app, ownerId).snapshot() }
    private val dao = AppDatabase.get(app).ledgerDao()
    val offlineProfiles = dao.observeOfflineOwners().map { owners ->
        owners.map { OfflineProfile(it, dao.recordCount(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun importOffline(oldOwner: String) = launch {
        require(oldOwner.startsWith("offline-")) { "Only a previous offline profile can be imported here." }
        LedgerRepository(AppDatabase.get(getApplication<Application>()), oldOwner) { CategoryLearningStore(getApplication<Application>(), oldOwner).snapshot() }.learningSnapshot()
        migrateOwner(oldOwner)
    }
    val accountNames=repo.profileBackup.map { it.workspace.accounts }.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),listOf("Cash","Bank","Easypaisa","JazzCash"))
    val transactions=repo.transactions.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val creditPurchases=repo.creditPurchases.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val creditPayments=repo.creditPayments.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val loans=repo.loans.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val loanPayments=repo.loanPayments.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committees=repo.committees.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committeePayments=repo.committeePayments.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committeeReceipts=repo.committeeReceipts.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committeeMembers=repo.committeeMembers.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerCircles=repo.organizer.circles.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerMembers=repo.organizer.members.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerCollections=repo.organizer.collections.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerPayouts=repo.organizer.payouts.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val savings=repo.savings.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val ledgerCycle=repo.ledgerCycle.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),null)
    val ledgerArchives=repo.ledgerArchives.distinctUntilChanged().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }
    init { viewModelScope.launch(Dispatchers.IO) {
        try {
            if (!dao.hasProfileBackup(ownerId)) repo.profileSnapshot()
            repo.ensureLedgerCycle(); repo.learningSnapshot()
            // Learned items join the picker from the first frame, and any
            // icons lost to a restore are re-downloaded while online.
            refreshLearnedOverlay(repo.learningSnapshot().items)
            backfillIcons()
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Saved data could not be loaded." }
    } }
    fun launch(
        onSuccess: () -> Unit = {},
        block: suspend LedgerRepository.() -> Unit,
    ) = viewModelScope.launch {
        try {
            saveChange(block)
            onSuccess()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Your change could not be saved. Please try again." }
    }
    /** Suspends until commit; callers keep their form open if validation or storage fails. */
    suspend fun saveChange(block: suspend LedgerRepository.() -> Unit) = withContext(Dispatchers.IO) {
        BackupLock.mutex.withLock {
            checkAccount()
            SettingsStore(getApplication<Application>()).markDrivePending(ownerId)
            repo.block()
        }
        // The database commit succeeded. A scheduler failure must not be presented as a failed save.
        // Pending status and the periodic worker retain the backup obligation.
        runCatching { SyncScheduler.syncNow(getApplication<Application>(), ownerId) }
        runCatching { com.sadique.dailyledger.widget.LedgerWidget.refresh(getApplication<Application>()) }
    }
    private val aiPrefs=AiPreferences(app,ownerId)
    private val aiService=AiService(app,ownerId)
    private val aiKeyStore=AiKeyStore(app,ownerId)
    fun aiKey():String=aiKeyStore.key
    fun saveAiKey(value:String){aiKeyStore.key=value}
    fun aiProxy():String=aiPrefs.proxyBase
    fun saveAiProxy(value:String){aiPrefs.proxyBase=value}
    fun aiProvider():String=aiPrefs.providerId
    fun saveAiProvider(value:String){aiPrefs.providerId=value}
    fun aiProviderModel():String=aiPrefs.providerModel
    fun saveAiProviderModel(value:String){aiPrefs.providerModel=value}
    fun aiProviderBase():String=aiPrefs.providerBase
    fun saveAiProviderBase(value:String){aiPrefs.providerBase=value}
    suspend fun testAiConnection():String{checkAccount();return aiService.testConnection().also{checkAccount()}}
    suspend fun aiAdvise(request:AiModelRequest):String{checkAccount();return aiService.advise(request).also{checkAccount()}}
    suspend fun importStatement(text:String):AiDraftResult = withContext(Dispatchers.Default) {
        checkAccount()
        val result = aiService.statementDrafts(text).also { checkAccount() }
        val learned = repo.learningSnapshot()
        val history = historyRules.value.filter { "${it.type}:${it.phrase}" !in learned.suppressed }
        val publicLearned=publicLearningRules(learned.rules,repo.transactions.first(),repo.profileSnapshot().workspace.revisions)
        val entries = result.entries.map { CategoryLearning.apply(it, publicLearned, history) }
        result.copy(entries = entries)
    }
    val salarySchedule = SalaryScheduleStore(app, ownerId, viewModelScope)
    val planner = com.sadique.dailyledger.planning.PlannerStore(app, ownerId, viewModelScope)
    private val _learningError = MutableStateFlow<String?>(null)
    val learningError = _learningError.asStateFlow()
    private val learning = repo.aiLearning.catch { e ->
        _learningError.value = e.message ?: "AI learning could not be read. Saved data was kept."
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, LearningSnapshot())
    private val historyRules = transactions.map { CategoryLearning.fromHistory(it.filterNot { row -> row.privateEntry }, ownerId) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val learnedCategories = combine(learning, historyRules, transactions, repo.profileBackup) { explicit, history, rows, profile ->
        publicLearningRules(explicit.rules,rows,profile.workspace.revisions) + history.filter { rule ->
            explicit.rules.none { it.type == rule.type && it.phrase == rule.phrase } && "${rule.type}:${CategoryLearning.normalize(rule.phrase)}" !in explicit.suppressed
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun changeLearning(change: (LearningSnapshot) -> LearningSnapshot): String? = try {
        withContext(Dispatchers.IO) {
            BackupLock.mutex.withLock {
                checkAccount()
                SettingsStore(getApplication<Application>()).markDrivePending(ownerId)
                repo.updateLearning(change)
            }
            SyncScheduler.syncNow(getApplication<Application>(), ownerId)
        }
        _learningError.value = null
        null
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { e.message ?: "Could not save AI learning. Please retry." }
    suspend fun forgetCategory(rule: LearnedCategoryRule) = changeLearning { it.forget(rule.type, rule.phrase) }
    suspend fun dismissInsight(tip: SpendingTip) = changeLearning { it.dismiss(tip) }
    suspend fun resetInsightFeedback() = changeLearning { it.copy(dismissedTips = emptySet()) }

    suspend fun saveRecurring(bill: com.sadique.dailyledger.planning.RecurringBill, draft: TransactionDraft) {
        val due = bill.nextDue()
        BackupLock.mutex.withLock {
            checkAccount()
            check(planner.data.value.bills.any { it.id == bill.id && it.nextDue() == due }) { "This reminder has already been handled. Refresh the list." }
            SettingsStore(getApplication<Application>()).markDrivePending(ownerId)
            repo.saveDraftBatch(listOf(draft), bill.batchId())
            planner.markHandled(bill.id, due, alreadyLocked = true)
        }
        runCatching { SyncScheduler.syncNow(getApplication<Application>(), ownerId) }
        runCatching { com.sadique.dailyledger.widget.LedgerWidget.refresh(getApplication<Application>()) }
    }
    private val manualInsights = ManualInsights(viewModelScope, aiPrefs.enabled, aiPrefs::cached, aiPrefs::save)
    val aiEnabled = manualInsights.enabled
    val aiTips = combine(manualInsights.tips, learning) { tips, data ->
        tips.filter { LearningSnapshot.feedbackKey(it) !in data.dismissedTips }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dismissedInsights = learning.map { it.dismissedTips }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val aiStatus = manualInsights.status
    val aiBusy = manualInsights.busy
    fun setAiEnabled(value: Boolean) { aiPrefs.enabled = value; manualInsights.setEnabled(value) }
    /** Persisted AI entry engine toggle (Task 2). Defaults to Online when Cloud
     * AI is enabled, else Offline; the user's explicit choice always wins. */
    private val _aiEntryEngine = MutableStateFlow(aiPrefs.entryEngine ?: if (aiEnabled.value) AiEntryEngine.ONLINE else AiEntryEngine.OFFLINE)
    /** Bumped whenever the learned-item overlay refreshes, so the shared
     * category options rebuild even when the transaction list is unchanged. */
    private val _learnedOverlayVersion = MutableStateFlow(0)
    val learnedOverlayVersion: StateFlow<Int> = _learnedOverlayVersion.asStateFlow()
    private fun refreshLearnedOverlay(items: List<LearnedItem>) {
        CategoryCatalog.setLearnedOverlay(items)
        _learnedOverlayVersion.value++
    }
    val aiEntryEngine: StateFlow<AiEntryEngine> = _aiEntryEngine.asStateFlow()
    fun saveAiEntryEngine(engine: AiEntryEngine) { aiPrefs.entryEngine = engine; _aiEntryEngine.value = engine }
    private fun checkAccount(){check(FirebaseRuntime.auth(getApplication<Application>()).currentUser?.uid==ownerId){"Please sign in again."}}
    private fun insightKey(snapshot: SpendingSnapshot) = AiPreferences.digest(AiProtocol.context(snapshot).toString())
    fun updateInsightContext(snapshot: SpendingSnapshot) {
        manualInsights.updateContext(insightKey(snapshot), snapshot.count > 0)
    }
    fun refreshInsights(snapshot: SpendingSnapshot) {
        manualInsights.refresh(insightKey(snapshot), snapshot.count > 0) {
            checkAccount()
            aiService.insights(snapshot).also { checkAccount() }
        }
    }
    /** Strict engine selection for the AI entry toggle. Null = legacy behaviour
     * (personal key -> online first with offline fallback; otherwise offline
     * first, cloud only when offline cannot parse). ONLINE and OFFLINE never
     * fall back to each other: a failure of the chosen engine surfaces as an
     * error, so the user always knows which engine produced (or failed) the entry. */
    suspend fun autoFill(input:String, engine:AiEntryEngine? = null):AiDraftResult = withContext(Dispatchers.Default) {
        checkAccount()
        require(!OfflineMiniAi.usesDedicatedLedger(input)) { "Udhar Saman, loans, savings aur kameti apne tabs mein record karein. Auto Fill sirf income aur expenses ke liye hai." }
        DraftReviewPolicy.checkMissingDetails(input)
        val offline = OfflineMiniAi.drafts(input)
        // Learned items are offered to the online model too, so a repeat
        // "Parday 3500" comes back with category Parday, not Other.
        val learnedSnapshot = repo.learningSnapshot()
        val result: AiDraftResult
        var usedCloud = false
        when (engine) {
            AiEntryEngine.ONLINE -> {
                result = aiService.drafts(input, learnedSnapshot.items).also { checkAccount() }
                usedCloud = true
            }
            AiEntryEngine.OFFLINE -> {
                result = offline ?: throw IllegalStateException("Offline AI is entry ko samajh nahi saka. Online AI try karein, ya amount aur saaf likhein.")
            }
            null -> {
                // A saved personal key means the online model: try it FIRST so categories
                // are understood properly, and only fall back to the offline parse when the
                // online call fails. Without a key, behaviour is unchanged (offline first,
                // cloud only when offline cannot parse and Cloud AI is enabled).
                val useOnlineFirst = aiEnabled.value && aiService.hasPersonalKey()
                if (useOnlineFirst) {
                    result = try {
                        aiService.drafts(input, learnedSnapshot.items).also { checkAccount() }.also { usedCloud = true }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { offline ?: throw e }
                } else if (offline != null) {
                    result = offline
                } else {
                    check(aiEnabled.value) { "Offline Mini AI could not understand this entry. Enable Cloud AI for complex wording, or enter amount more clearly." }
                    result = aiService.drafts(input, learnedSnapshot.items).also { checkAccount() }
                    usedCloud = true
                }
            }
        }
        val learned = learnedSnapshot
        val history = historyRules.value.filter { "${it.type}:${it.phrase}" !in learned.suppressed }
        val publicLearned=publicLearningRules(learned.rules,repo.transactions.first(),repo.profileSnapshot().workspace.revisions)
        val entries = result.entries.map { CategoryLearning.apply(it, publicLearned, history) }
        val learnedCount = entries.zip(result.entries).count { (updated, original) -> updated.category != original.category }
        result.copy(entries = entries, reviewIssues = entries.map { DraftReviewPolicy.issues(input, it, cloud = usedCloud) },
            message = result.message + if (learnedCount > 0) " $learnedCount categories matched your saved learning." else "")
    }
    suspend fun rememberCategory(type:String,phrase:String,category:String):String? =
        changeLearning { it.remember(type, phrase, category) }
    suspend fun saveAiDrafts(drafts:List<TransactionDraft>,batchId:String,fromOnline:Boolean? = null){
        val online = fromOnline ?: (_aiEntryEngine.value == AiEntryEngine.ONLINE)
        BackupLock.mutex.withLock{checkAccount();SettingsStore(getApplication<Application>()).markDrivePending(ownerId);repo.saveDraftBatch(drafts,batchId)}
        if (online) {
            // Items evolve: learn every real item name from the SAVED drafts
            // (post-review, so user edits win), then refresh the picker and
            // fetch original icons in the background. Learning never fails
            // an already-saved entry, and the entry never waits for icons.
            runCatching { withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val catalog = CategoryCatalog.load(app)
                val before = repo.learningSnapshot()
                val fresh = drafts.mapNotNull { draft ->
                    val name = ItemNames.extractItemName(draft.note, null, drafts.size == 1, draft.category) ?: return@mapNotNull null
                    if (LearnedItems.isKnown(name, draft.type, catalog, before.items)) null
                    else LearnedItem(name, draft.type, draft.category)
                }
                if (fresh.isNotEmpty()) {
                    BackupLock.mutex.withLock {
                        checkAccount()
                        SettingsStore(app).markDrivePending(ownerId)
                        repo.updateLearning { it.learnItems(fresh) }
                    }
                    refreshLearnedOverlay(repo.learningSnapshot().items)
                }
                backfillIcons(fresh)
            } }
        }
        runCatching{SyncScheduler.syncNow(getApplication<Application>(), ownerId)}
    }
    /**
     * Background original-icon fetch: new learned items first, then older
     * learned items still missing icons, then built-in labels. At most 8 new
     * downloads per run — no network storm, no UI block, offline = silent
     * skip (the default icon shows until a later run succeeds). Each working
     * source URL is recorded in the snapshot so a restore can re-download
     * the same icon; the next regular backup sync carries the record.
     */
    private fun backfillIcons(newItems: List<LearnedItem> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val fresh = repo.learningSnapshot()
                val queue = mutableListOf<Pair<String, String?>>()
                val seen = mutableSetOf<String>()
                fun offer(label: String, url: String?) {
                    val key = LearnedItems.normalizeName(label)
                    if (key.isBlank() || key in seen) return
                    seen += key
                    if (!ItemIconService.hasIcon(app, label)) queue += label to url
                }
                newItems.forEach { offer(it.name, it.iconUrl) }
                fresh.items.forEach { offer(it.name, it.iconUrl) }
                CategoryCatalog.load(app).forEach { offer(it.label, null) }
                var stored = 0
                for ((label, url) in queue) {
                    if (stored >= 8) break
                    val source = ItemIconService.ensureIcon(app, label, url) ?: continue
                    stored++
                    val item = fresh.items.firstOrNull { LearnedItems.normalizeName(it.name) == LearnedItems.normalizeName(label) }
                    if (item != null) {
                        BackupLock.mutex.withLock {
                            checkAccount()
                            SettingsStore(app).markDrivePending(ownerId)
                            repo.updateLearning { it.withItemIcon(item.name, item.type, source) }
                        }
                    }
                }
            }
        }
    }
    fun startNewMonth() = launch { startNewLedgerCycle() }
    fun archivePreviousMonth(onSuccess:(Boolean)->Unit) = viewModelScope.launch {
        try {
            val archived = BackupLock.mutex.withLock { checkAccount(); SettingsStore(getApplication<Application>()).markDrivePending(ownerId); repo.archivePreviousCalendarMonth() }
            onSuccess(archived)
            SyncScheduler.syncNow(getApplication<Application>(), ownerId)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Could not archive last month." }
    }
    suspend fun saveCreditDrafts(drafts:List<CreditDraft>,batchId:String){BackupLock.mutex.withLock{checkAccount();SettingsStore(getApplication<Application>()).markDrivePending(ownerId);repo.saveCreditDrafts(drafts,batchId)};runCatching{SyncScheduler.syncNow(getApplication<Application>(), ownerId)}}
    suspend fun saveCreditPayment(id:String,amount:Long,date:String,note:String,method:String){BackupLock.mutex.withLock{checkAccount();SettingsStore(getApplication<Application>()).markDrivePending(ownerId);repo.addCreditPayment(id,amount,date,note,method)};runCatching{SyncScheduler.syncNow(getApplication<Application>(), ownerId)}}
    class Factory(private val app:Application,private val owner:String):ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T=MainViewModel(app,owner) as T}
}
