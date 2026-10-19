package com.sadique.dailyledger.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Temporary private drafts also protect the OS screenshot/recents surface, before a record is saved. */
object SensitiveEditors {
    private val count=MutableStateFlow(0)
    val active=count.asStateFlow()
    fun enter(){count.update{it+1}}
    fun leave(){count.update{(it-1).coerceAtLeast(0)}}
}
