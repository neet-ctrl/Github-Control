package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhUser
import com.githubcontrol.data.auth.Account
import com.githubcontrol.data.auth.AccountManager
import com.githubcontrol.data.auth.SessionGate
import com.githubcontrol.data.auth.TokenValidator
import com.githubcontrol.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AuthState(
    val loggedIn: Boolean = false,
    val locked: Boolean = true,
    val activeLogin: String? = null,
    val activeAvatar: String? = null,
    val rateRemaining: Int? = null,
    val accounts: List<Account> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    val accountManager: AccountManager,
    private val sessionGate: SessionGate,
    private val repo: GitHubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _addingAccount = MutableStateFlow(false)
    val addingAccount: StateFlow<Boolean> = _addingAccount.asStateFlow()

    /**
     * Emitted whenever the active account is switched. AppRoot listens to this
     * and re-anchors the nav back stack to the Dashboard so every screen's
     * ViewModel is recreated and reloads using the new account's token —
     * otherwise screens keep showing data cached from the previous account.
     */
    private val _accountSwitched = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val accountSwitched: SharedFlow<String> = _accountSwitched.asSharedFlow()

    fun beginAddAccount() { _addingAccount.value = true }
    fun endAddAccount() { _addingAccount.value = false }

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val accounts = accountManager.accountsBlocking()
            val active = accountManager.activeAccount()
            _state.value = _state.value.copy(
                loggedIn = active != null,
                accounts = accounts,
                activeLogin = active?.login,
                activeAvatar = active?.avatarUrl,
                locked = sessionGate.locked.value
            )
        }
    }

    fun signInWithToken(token: String, onDone: () -> Unit) {
        if (token.isBlank()) { _loginError.value = "Token is empty"; return }
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            val result = withContext(Dispatchers.IO) { TokenValidator.validate(token) }
            if (!result.ok || result.login == null) {
                _loginError.value = result.error ?: "Invalid token (${result.validation.httpCode ?: "no response"})"
                _loginBusy.value = false
                return@launch
            }
            val acc = Account(
                id = result.login!!,
                login = result.login!!,
                avatarUrl = result.avatarUrl ?: "",
                name = result.name,
                email = result.email,
                token = token,
                scopes = result.scopes,
                tokenType = result.validation.tokenType,
                tokenExpiry = result.validation.tokenExpiry,
                lastValidatedAt = result.validation.ts,
                validations = listOf(result.validation)
            )
            accountManager.addOrReplaceAccount(acc, makeActive = true)
            sessionGate.unlock()
            refresh()
            _loginBusy.value = false
            onDone()
        }
    }

    /** Re-validate the active account's token. Returns the freshly stored validation. */
    suspend fun revalidateActive(): com.githubcontrol.data.auth.TokenValidation? {
        val acc = accountManager.activeAccount() ?: return null
        val res = withContext(Dispatchers.IO) { TokenValidator.validate(acc.token) }
        accountManager.recordValidation(acc.id, res.validation)
        refresh()
        return res.validation
    }

    /** Validate an arbitrary token without saving it (used by the rich login form preview). */
    suspend fun previewValidate(token: String): TokenValidator.Result =
        withContext(Dispatchers.IO) { TokenValidator.validate(token) }

    fun unlock() { sessionGate.unlock(); refresh() }
    fun lock() { sessionGate.lock(); refresh() }

    fun logoutActive() {
        viewModelScope.launch {
            val a = accountManager.activeAccount() ?: return@launch
            accountManager.removeAccount(a.id)
            refresh()
        }
    }

    fun switchAccount(id: String) {
        viewModelScope.launch {
            accountManager.setActive(id)
            sessionGate.unlock()
            refresh()
            // Notify AppRoot so it can clear the back stack & reload screens
            // under the newly-active token.
            _accountSwitched.tryEmit(id)
        }
    }

    fun wipeAll() {
        viewModelScope.launch { accountManager.wipe(); sessionGate.lock(); refresh() }
    }
}
