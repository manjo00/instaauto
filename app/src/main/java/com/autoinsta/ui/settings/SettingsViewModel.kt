package com.autoinsta.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.AccountEntity
import com.autoinsta.data.repository.AccountRepository
import com.autoinsta.data.remote.OAuthRedirectBus
import com.autoinsta.data.repository.ConnectResult
import com.autoinsta.domain.TokenLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val account: AccountEntity? = null,
    val isConnecting: Boolean = false,
    /** True from tapping Connect until the browser hands a result back. */
    val awaitingBrowser: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val daysRemaining: Int = 0,
    val tokenState: TokenLifecycle.State = TokenLifecycle.State.Healthy,
)

/**
 * Drives the Settings screen: connecting an Instagram account, showing its status, and
 * disconnecting.
 */
class SettingsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        // The browser delivers the login result to MainActivity, which posts it here.
        viewModelScope.launch {
            OAuthRedirectBus.result.collect { pending ->
                when (val r = pending ?: return@collect) {
                    is OAuthRedirectBus.Result.Code -> {
                        OAuthRedirectBus.clear()
                        exchangeCode(r.value)
                    }
                    is OAuthRedirectBus.Result.Error -> {
                        OAuthRedirectBus.clear()
                        _uiState.update {
                            it.copy(awaitingBrowser = false, isConnecting = false, errorMessage = r.message)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            accountRepository.observe().collect { account ->
                _uiState.update {
                    it.copy(
                        account = account,
                        daysRemaining = accountRepository.daysRemaining(),
                        tokenState = accountRepository.tokenState(),
                    )
                }
            }
        }
    }

    /** Called once the browser has actually been opened. */
    fun onBrowserOpened() {
        _uiState.update { it.copy(awaitingBrowser = true, errorMessage = null, successMessage = null) }
    }

    fun onBrowserFailed() {
        _uiState.update {
            it.copy(awaitingBrowser = false, errorMessage = "Couldn't open a browser to log in.")
        }
    }

    /**
     * The user came back without completing the login (pressed back in the browser).
     * Called when Settings becomes visible again with nothing pending.
     */
    fun onReturnedWithoutResult() {
        if (_uiState.value.awaitingBrowser && OAuthRedirectBus.result.value == null) {
            _uiState.update { it.copy(awaitingBrowser = false) }
        }
    }

    /** Turn the login code from the browser into a connected account. */
    private fun exchangeCode(code: String) {
        _uiState.update { it.copy(awaitingBrowser = false, isConnecting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = accountRepository.connectWithCode(code)) {
                is ConnectResult.Success -> _uiState.update {
                    it.copy(
                        isConnecting = false,
                        successMessage = "Connected as @${result.username}",
                        daysRemaining = accountRepository.daysRemaining(),
                        tokenState = accountRepository.tokenState(),
                    )
                }
                is ConnectResult.Failure -> _uiState.update {
                    it.copy(isConnecting = false, errorMessage = result.message)
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            accountRepository.disconnect()
            _uiState.update {
                it.copy(successMessage = null, errorMessage = null, daysRemaining = 0)
            }
        }
    }

    fun consumeMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
