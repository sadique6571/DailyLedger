package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon

import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun LockedScreen(unlock:()->Unit){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){Icon(Icons.Default.Lock,null,Modifier.size(64.dp));Text("Daily Ledger is locked",style=MaterialTheme.typography.headlineSmall);Button(onClick=unlock){Text("Unlock")}}}}
