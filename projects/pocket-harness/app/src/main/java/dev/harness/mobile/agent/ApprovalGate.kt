package dev.harness.mobile.agent

import dev.harness.mobile.data.ApprovalRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 审批门：Agent 在真正执行敏感工具前挂起，等界面上用户点"允许/拒绝"。
 */
class ApprovalGate {

    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending: StateFlow<ApprovalRequest?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<Boolean>? = null

    suspend fun request(request: ApprovalRequest): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        synchronized(this) { deferred = waiter }
        _pending.value = request
        return try {
            waiter.await()
        } finally {
            _pending.value = null
            synchronized(this) { if (deferred === waiter) deferred = null }
        }
    }

    fun answer(approved: Boolean) {
        val waiter = synchronized(this) { deferred }
        if (waiter != null && !waiter.isCompleted) waiter.complete(approved)
    }

    fun cancelAll() = answer(false)
}