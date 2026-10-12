package com.sadique.dailyledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sadique.dailyledger.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
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
            BackupLock.mutex.withLock {
                check(FirebaseRuntime.auth(getApplication<Application>()).currentUser?.uid == ownerId) {
                    "This account is no longer signed in. Please sign in again."
                }
                repo.block()
            }
            onSuccess()
            SyncScheduler.syncNow(getApplication<Application>())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Your change could not be saved. Please try again." }
    }
    private val aiPrefs=AiPreferences(app,ownerId)
    private val aiService=AiService(app,ownerId)
    private val categoryLearning=CategoryLearningStore(app,ownerId)
    val aiEnabled=MutableStateFlow(aiPrefs.enabled)
    val aiTips=MutableStateFlow<List<SpendingTip>>(emptyList())
    val aiStatus=MutableStateFlow("")
    private var aiJob:Job?=null
    private var requestedHash=""
    fun setAiEnabled(value:Boolean){aiPrefs.enabled=value;aiEnabled.value=value;aiJob?.cancel();aiTips.value=emptyList();aiStatus.value=""}
    private fun checkAccount(){check(FirebaseRuntime.auth(getApplication<Application>()).currentUser?.uid==ownerId){"Please sign in again."}}
    fun refreshInsights(snapshot:SpendingSnapshot){
        if(!aiEnabled.value||snapshot.count==0){aiTips.value=emptyList();return}
        val hash=AiPreferences.digest(AiProtocol.context(snapshot).toString());requestedHash=hash
        aiPrefs.cached(hash)?.let{aiTips.value=it;aiStatus.value="AI suggestions • recorded totals";return}
        aiTips.value=emptyList()
        if(aiJob?.isActive==true)return
        if(System.currentTimeMillis()-aiPrefs.attemptedAt<60*60000L){aiStatus.value="Local insights are current. Cloud suggestions refresh later to limit cloud usage.";return}
        aiJob=viewModelScope.launch{
            aiPrefs.attemptedAt=System.currentTimeMillis();aiStatus.value="Preparing AI suggestions…"
            try{checkAccount();val tips=aiService.insights(snapshot);checkAccount();if(aiEnabled.value&&requestedHash==hash){aiPrefs.save(hash,tips);aiTips.value=tips;aiStatus.value="AI suggestions • recorded totals"}}
            catch(e:CancellationException){throw e}catch(e:Exception){aiStatus.value=(e as? AiException)?.message?:"AI suggestions are unavailable. Local insights still work."}
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
        return result.copy(entries = result.entries.map { CategoryLearning.apply(it, learnedRules) })
    }
    fun rememberCategory(type:String,phrase:String,category:String):String? = try{
        checkAccount()
        categoryLearning.remember(type,phrase,category)
        null
    }catch(e:Exception){e.message?:"Could not save this category correction on this device."}
    suspend fun saveAiDrafts(drafts:List<TransactionDraft>,batchId:String){BackupLock.mutex.withLock{checkAccount();repo.saveDraftBatch(drafts,batchId)};runCatching{SyncScheduler.syncNow(getApplication<Application>())}}
    fun startNewMonth() = launch { startNewLedgerCycle() }
    fun archivePreviousMonth(onSuccess:(Boolean)->Unit) = viewModelScope.launch {
        try {
            val archived = BackupLock.mutex.withLock { checkAccount(); repo.archivePreviousCalendarMonth() }
            onSuccess(archived)
            SyncScheduler.syncNow(getApplication<Application>())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Could not archive last month." }
    }
    suspend fun saveCreditDrafts(drafts:List<CreditDraft>,batchId:String){BackupLock.mutex.withLock{checkAccount();repo.saveCreditDrafts(drafts,batchId)};runCatching{SyncScheduler.syncNow(getApplication<Application>())}}
    suspend fun saveCreditPayment(id:String,amount:Long,date:String,note:String,method:String){BackupLock.mutex.withLock{checkAccount();repo.addCreditPayment(id,amount,date,note,method)};runCatching{SyncScheduler.syncNow(getApplication<Application>())}}
    class Factory(private val app:Application,private val owner:String):ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T=MainViewModel(app,owner) as T}
}
