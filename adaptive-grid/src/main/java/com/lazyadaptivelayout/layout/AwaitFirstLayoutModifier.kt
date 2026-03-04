package com.lazyadaptivelayout.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.registerOnLayoutRectChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.CompletableDeferred

/**
 * Internal modifier which allows to delay some interactions (e.g. scroll) until layout is ready.
 */
internal class AwaitFirstLayoutModifier : ModifierNodeElement<AwaitFirstLayoutModifier.Node>() {
    private var attachedNode: Node? = null
    private var lock: CompletableDeferred<Unit>? = null

    suspend fun waitForFirstLayout() {
        val lock =
            lock
                ?: CompletableDeferred<Unit>().also {
                    this.lock = it
                    val node = attachedNode
                    node?.requestOnAfterLayoutCallback()
                }
        lock.await()
    }

    override fun create() = Node()

    override fun update(node: Node) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "AwaitFirstLayoutModifier"
    }

    override fun hashCode(): Int = 234

    override fun equals(other: Any?): Boolean = other === this

    inner class Node : Modifier.Node() {
        override fun onAttach() {
            attachedNode = this
            if (lock != null) {
                requestOnAfterLayoutCallback()
            }
        }

        private var handle: DelegatableNode.RegistrationHandle? = null

        fun requestOnAfterLayoutCallback() {
            handle =
                registerOnLayoutRectChanged(0, 0) {
                    handle?.unregister()
                    handle = null
                    lock?.complete(Unit)
                    lock = null
                }
        }

        override fun onDetach() {
            if (attachedNode === this) {
                attachedNode = null
            }
            handle?.unregister()
            handle = null
        }
    }
}

