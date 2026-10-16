package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.CategoryCatalog
import com.sadique.dailyledger.ui.GlowIcon
import com.sadique.dailyledger.ui.categoryVisual
@Composable fun CategoryPicker(type:String,onSelect:(String)->Unit,onDismiss:()->Unit,allowCustom:Boolean=true){
    val context=LocalContext.current;val all=remember{CategoryCatalog.load(context)};var query by remember{mutableStateOf("")}
    val filtered=all.filter{it.type==type&&(query.isBlank()||it.label.contains(query,true)||it.group.contains(query,true)||it.aliases.any{a->a.contains(query,true)})}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Choose category")},text={Column{
        OutlinedTextField(query,{query=it},label={Text("Search categories")},singleLine=true)
        LazyColumn(Modifier.heightIn(max=350.dp)){
            filtered.groupBy{it.group}.forEach{(group,rows)->
                item{Text(group,Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
                items(rows,key={it.label}){r->
                    val visual=categoryVisual(r.label,r.type)
                    Row(Modifier.fillMaxWidth().clickable{onSelect(r.label)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                        GlowIcon(visual.icon,visual.color,size=34.dp)
                        Text(r.label,Modifier.padding(start=11.dp))
                    }
                }
            }
            if(allowCustom&&query.isNotBlank()&&filtered.none{it.label.equals(query.trim(),true)})item{TextButton(onClick={onSelect(query.trim().take(60))}){Text("Use custom: ${query.take(60)}")}}
        }
    }},confirmButton={TextButton(onClick=onDismiss){Text("Close")}})
}
