package com.sadique.dailyledger.ui.screens

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sadique.dailyledger.ai.OfflineMiniAi
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.*
import com.sadique.dailyledger.i18n.LocalizedText as Text
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

@Composable internal fun FinanceIntakePanel(history:List<TransactionEntity>,accounts:List<String>,owner:String,onSave:suspend(List<TransactionDraft>,String,String)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var source by remember{mutableStateOf("Receipt")};var input by remember{mutableStateOf("")}
    var date by remember{mutableStateOf(LocalDate.now().toString())};var direction by remember{mutableStateOf("EXPENSE")}
    var account by remember{mutableStateOf("Cash")};var total by remember{mutableStateOf("")}
    var rows by remember{mutableStateOf(emptyList<TransactionDraft>())};var reviewed by remember{mutableStateOf(false)}
    var batch by remember{mutableStateOf(UUID.randomUUID().toString())};var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)};var success by remember{mutableStateOf<String?>(null)};var editing by remember{mutableStateOf<Int?>(null)}
    var alive by remember{mutableStateOf(true)}
    DisposableEffect(Unit){onDispose{alive=false}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null && !busy){busy=true;error=null;rows=emptyList();reviewed=false
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image=InputImage.fromFilePath(context,uri)
                recognizer.process(image).addOnSuccessListener{result->
                    if(alive){val extracted=recognizedReceiptText(result);if(extracted.length<=20000){input=extracted;source="Receipt"}else error="Receipt is too long. Use smaller sections."}
                }.addOnFailureListener{if(alive)error="Could not read this image. Try a clearer Latin-script receipt or type the items."}
                    .addOnCompleteListener{recognizer.close();if(alive)busy=false}
            } catch(e:Exception){recognizer.close();error=e.message;busy=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Images are read on this phone using the bundled Latin-script model. Urdu-script OCR is not supported. Photos and full SMS text are not added to your backup; only reviewed entries are saved.")
        FinanceSelect("Source",source,listOf("Receipt","Bank SMS","Voice / typed"),{source=it;rows=emptyList();reviewed=false},!busy)
        if(source=="Receipt")Button(onClick={picker.launch("image/*")},enabled=!busy){Text("Choose receipt photo")}
        if(source=="Voice / typed")OfflineVoiceControl(enabled=!busy,onText={input=it;rows=emptyList();reviewed=false},onError={error=it})
        LedgerTextField(input,{input=it.take(20000);rows=emptyList();reviewed=false},label={Text(if(source=="Bank SMS")"Paste payment SMS" else "Review extracted text / describe entries")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,enabled=!busy)
        if(source=="Bank SMS")FinanceSelect("Payment direction",direction,listOf("EXPENSE","INCOME"),{direction=it;rows=emptyList();reviewed=false},!busy)
        FinanceSelect("Account",account,accounts,{account=it},!busy)
        LedgerDateField(date,{date=it;rows=emptyList();reviewed=false},label="Entry date",enabled=!busy)
        if(source=="Receipt")LedgerTextField(total,{total=it;reviewed=false},label={Text("Receipt total PKR (recommended)")},enabled=!busy)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)};success?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
        Button(enabled=!busy && input.isNotBlank() && isValidDate(date),onClick={scope.launch{
            busy=true;error=null;success=null;reviewed=false
            try{rows=withContext(Dispatchers.Default){when(source){"Receipt"->FinanceIntake.receipt(input,date);"Bank SMS"->listOf(FinanceIntake.sms(input,date,direction));else->requireNotNull(OfflineMiniAi.drafts(input)){"Offline parsing could not understand this. Type clear item amounts or use the normal Auto Fill page."}.entries}}
                batch=UUID.randomUUID().toString()
            }catch(e:Exception){if(e is kotlin.coroutines.cancellation.CancellationException)throw e;error=e.message;rows=emptyList()}finally{busy=false}
        }}){Text(if(busy)"Working…" else "Prepare for review")}
        if(rows.isNotEmpty()){
            Text("Review ${rows.size} entries. Tap any row to correct its date, category or amount.")
            rows.forEachIndexed { index,row -> LedgerCard(Modifier.fillMaxWidth().clickable(enabled=!busy){editing=index},contentSpacing=4.dp){
                Text(row.note,translate=false);Text("${row.date} • ${row.category} • ${money(row.amountMinor)}")
                if(history.any{it.type==row.type && it.amountMinor==row.amountMinor && it.date==row.date && it.category.equals(row.category,true)})Text("Possible duplicate already in your ledger",color=MaterialTheme.colorScheme.error)
                if(row.category=="Other")Text("Check category",color=MaterialTheme.colorScheme.error)
            } }
            val sum=rows.sumOf{it.amountMinor};val expected=total.takeIf{it.isNotBlank()}?.let(::parseMinor)
            Text("Prepared total: ${money(sum)}")
            val totalsMatch=source!="Receipt" || total.isBlank() || expected==sum
            if(!totalsMatch)Text("Prepared amounts do not match the receipt total. Correct the rows or the total before saving.",color=MaterialTheme.colorScheme.error)
            Row{Checkbox(reviewed,{reviewed=it},enabled=!busy);Text("I checked every date, category, amount and duplicate warning")}
            Button(enabled=reviewed && totalsMatch && !busy,onClick={val checked=rows.toList();scope.launch{busy=true;error=null;try{onSave(checked,account,batch);rows=emptyList();input="";reviewed=false;success="Entries saved"}catch(e:Exception){if(e is kotlin.coroutines.cancellation.CancellationException)throw e;error=e.message}finally{busy=false}}}){Text("Save reviewed entries")}
        }
    }
    editing?.let { index -> rows.getOrNull(index)?.let { draft ->
        val now=System.currentTimeMillis()
        FinanceEntryEditor(owner,accounts,null,TransactionEntity("",owner,draft.type,draft.amountMinor,draft.category,draft.note,draft.date,now,now,account),false,{editing=null}) { row ->
            check(!row.privateEntry && row.quantity.isBlank()){ "Use New detailed entry for private or quantity/rate entries." }
            rows=rows.mapIndexed{i,old->if(i==index)TransactionDraft(row.type,row.amountMinor,row.category,row.note,row.date) else old};reviewed=false
        }
    } }
}

@Composable private fun OfflineVoiceControl(enabled:Boolean,onText:(String)->Unit,onError:(String)->Unit) {
    val reportError=onError
    val context=LocalContext.current;var recognizer by remember{mutableStateOf<SpeechRecognizer?>(null)}
    var listening by remember{mutableStateOf(false)};var language by remember{mutableStateOf("en-PK")}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){recognizer?.destroy();recognizer=null;listening=false}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);recognizer?.destroy()}}
    fun start() {
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)){onError("Offline voice is unavailable on this phone. Use typed entry; no online voice fallback is used.");return}
        recognizer?.destroy()
        try {
            val service=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);recognizer=service
            service.setRecognitionListener(object:RecognitionListener{
                override fun onReadyForSpeech(params:Bundle?){listening=true}
                override fun onBeginningOfSpeech(){}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){}
                override fun onError(error:Int){listening=false;reportError("Offline voice could not finish (code $error). Check the phone's offline language pack or type the entry.")}
                override fun onResults(results:Bundle?){listening=false;results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)}
                override fun onPartialResults(partialResults:Bundle?){}
                override fun onEvent(eventType:Int,params:Bundle?){}
            })
            listening=true
            service.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,language).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true))
        }catch(e:Exception){listening=false;onError(e.message ?: "Offline voice could not start.")}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)start()else onError("Microphone permission was declined. Typed entry still works.")}
    Column {
        FinanceSelect("Voice language",language,listOf("en-PK","ur-PK"),{language=it},enabled&&!listening)
        Button(enabled=enabled,onClick={if(listening){recognizer?.stopListening()}else if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context))reportError("Offline voice is unavailable on this phone. Use typed entry; no online voice fallback is used.") else permission.launch(Manifest.permission.RECORD_AUDIO)}){Text(if(listening)"Finish listening" else "Speak offline")}
        Text("Requires Android 12+ and a supported offline recognition service/language pack. Review the transcript before preparing entries.")
    }
}
