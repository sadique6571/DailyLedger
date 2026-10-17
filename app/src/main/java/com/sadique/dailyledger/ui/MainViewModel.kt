package com.sadique.dailyledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.sadique.dailyledger.ai.*
import com.sadique.dailyledger.sync.SyncScheduler
import com.sadique.dailyledger.sync.BackupLock
import kotlinx.coroutines.sync.withLock
import com.sadique.dailyledger.auth.FirebaseRuntime
import kotlin.coroutines.cancellation.CancellationException

data class OfflineProfile(val ownerId: String, val records: Int)

class MainViewModel(app:Application,val ownerId:String):AndroidViewModel(app){
    val repo=LedgerRepository(AppDatabase.get(app),ownerId)
    private val dao = AppDatabase.get(app).ledgerDao()
    val offlineProfiles = dao.observeOfflineOwners().map { owners ->
        owners.map { OfflineProfile(it, dao.recordCount(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun importOffline(oldOwner: String) = launch {
        require(oldOwner.startsWith("offline-")) { "Only a previous offline profile can be imported here." }
        migrateOwner(oldOwner)
    }
    val transactions=repo.transactions.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val creditPurchases=repo.creditPurchases.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val creditPayments=repo.creditPayments.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val loans=repo.loans.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val loanPayments=repo.loanPayments.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committees=repo.committees.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committeePayments=repo.committeePayments.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committeeReceipts=repo.committeeReceipts.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val committeeMembers=repo.committeeMembers.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerCircles=repo.organizer.circles.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerMembers=repo.organizer.members.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerCollections=repo.organizer.collections.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val organizerPayouts=repo.organizer.payouts.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val savings=repo.savings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val ledgerCycle=repo.ledgerCycle.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),null)
    val ledgerArchives=repo.ledgerArchives.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }
    init { viewModelScope.launch { repo.ensureLedgerCycle() } }
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
    suspend fun saveChange(block: suspend LedgerRepository.() -> Unit) {
        BackupLock.mutex.withLock {
            checkAccount()
            SettingsStore(getApplication<Application>()).markDrivePending(ownerId)
            repo.block()
        }
        // The database commit succeeded. A scheduler failure must not be presented as a failed save.
        // Pending status and the periodic worker retain the backup obligation.
        runCatching { SyncScheduler.syncNow(getApplication<Application>(), ownerId) }
    }
    private val aiPrefs=AiPreferences(app,ownerId)
    private val aiService=AiService(app,ownerId)
    private val categoryLearning=CategoryLearningStore(app,ownerId)
    val salarySchedule = SalaryScheduleStore(app, ownerId)
    val planner = com.sadique.dailyledger.planning.PlannerStore(app, ownerId)
    private val learningRevision = MutableStateFlow(0)
    private val _learningError = MutableStateFlow<String?>(null)
    val learningError = _learningError.asStateFlow()
    val learnedCategories = combine(transactions, learningRevision) { rows, _ ->
        try {
        val explicit = categoryLearning.rules()
        val suppressed = categoryLearning.suppressed()
        _learningError.value = null
        explicit + CategoryLearning.fromHistory(rows, ownerId).filter { rule ->
            explicit.none { it.type == rule.type && it.phrase == rule.phrase } && "${rule.type}:${CategoryLearning.normalize(rule.phrase)}" !in suppressed
        }
        } catch (e: Exception) {
            _learningError.value = "Saved AI learning could not be read. Existing files were kept."
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun forgetCategory(rule: LearnedCategoryRule): String? = try {
        checkAccount(); categoryLearning.forget(rule.type, rule.phrase); learningRevision.value++; null
    } catch (e: Exception) { e.message ?: "Could not remove this learning rule." }
    suspend fun saveRecurring(bill: com.sadique.dailyledger.planning.RecurringBill, draft: TransactionDraft) {
        val due = bill.nextDue()
        BackupLock.mutex.withLock {
            checkAccount()
            check(planner.data.value.bills.any { it.id == bill.id && it.nextDue() == due }) { "This reminder has already been handled. Refresh the list." }
            SettingsStore(getApplication<Application>()).markDrivePending(ownerId)
            repo.saveDraftBatch(listOf(draft), bill.batchId())
            planner.markHandled(bill.id, due)
        }
        runCatching { SyncScheduler.syncNow(getApplication<Application>(), ownerId) }
    }
    private val manualInsights = ManualInsights(viewModelScope, aiPrefs.enabled, aiPrefs::cached, aiPrefs::save)
    val aiEnabled = manualInsights.enabled
    val aiTips = manualInsights.tips
    val aiStatus = manualInsights.status
    val aiBusy = manualInsights.busy
    fun setAiEnabled(value: Boolean) { aiPrefs.enabled = value; manualInsights.setEnabled(value) }
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
    suspend fun autoFill(input:String):AiDraftResult{
        checkAccount()
        require(!OfflineMiniAi.usesDedicatedLedger(input)) { "Udhar Saman, loans, savings aur kameti apne tabs mein record karein. Auto Fill sirf income aur expenses ke liye hai." }
        val result = OfflineMiniAi.drafts(input) ?: run {
            check(aiEnabled.value){"Offline Mini AI could not understand this entry. Enable Cloud AI for complex wording, or enter amount more clearly."}
            aiService.drafts(input).also { checkAccount() }
        }
        val learnedRules = categoryLearning.rules()
        val suppressed = categoryLearning.suppressed()
        val historyRules = CategoryLearning.fromHistory(repo.transactions.first(), ownerId).filter { "${it.type}:${CategoryLearning.normalize(it.phrase)}" !in suppressed }
        val entries = result.entries.map { CategoryLearning.apply(it, learnedRules, historyRules) }
        val learnedCount = entries.zip(result.entries).count { (updated, original) -> updated.category != original.category }
        return result.copy(entries = entries, message = result.message +
            if (learnedCount > 0) " $learnedCount categories matched your earlier corrections or saved purchases." else "")
    }
    fun rememberCategory(type:String,phrase:String,category:String):String? = try{
        checkAccount()
        categoryLearning.remember(type,phrase,category)
        learningRevision.value++
        null
    }catch(e:Exception){e.message?:"Could not save this category correction on this device."}
    suspend fun saveAiDrafts(drafts:List<TransactionDraft>,batchId:String){BackupLock.mutex.withLock{checkAccount();SettingsStore(getApplication<Application>()).markDrivePending(ownerId);repo.saveDraftBatch(drafts,batchId)};runCatching{SyncScheduler.syncNow(getApplication<Application>(), ownerId)}}
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
