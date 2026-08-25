package com.autoinsta.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoinsta.data.db.entities.AccountEntity
import com.autoinsta.data.repository.AccountRepository
import com.autoinsta.data.repository.ConnectResult
import com.autoinsta.domain.TokenLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val account: AccountEntity? = null,
    val isConnecting: Boolean = false,
    val showLogin: Boolean = false,
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

    fun startConnect() {
        _uiState.update { it.copy(showLogin = true, errorMessage = null, successMessage = null) }
    }

    fun cancelConnect() {
        _uiState.update { it.copy(showLogin = false) }
    }

    /** The WebView caught a login code — turn it into a connected account. */
    fun onCodeReceived(code: String) {
        _uiState.update { it.copy(showLogin = false, isConnecting = true, errorMessage = null) }
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

    fun onLoginError(message: String) {
        _uiState.update { it.copy(showLogin = false, isConnecting = false, errorMessage = message) }
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
