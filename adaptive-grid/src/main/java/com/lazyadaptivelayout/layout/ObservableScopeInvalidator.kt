package com.lazyadaptivelayout.layout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import kotlin.jvm.JvmInline

@JvmInline
internal value class ObservableScopeInvalidator(
    private val state: MutableState<Unit> = mutableStateOf(Unit, neverEqualPolicy())
) {
    fun attachToScope() {
        state.value
    }

    fun invalidateScope() {
        state.value = Unit
    }
}

