package com.example.coupgame

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GameViewModel : ViewModel() {

    private val apiService = ApiService()
    val gameState = GameState()

    private val _gameId = MutableStateFlow<String?>(null)
    val gameId: StateFlow<String?> = _gameId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val localPlayerId: String = UUID.randomUUID().toString()
    private var localPlayerName = ""

    fun createOnlineGame(playerName: String) {
        localPlayerName = playerName
        viewModelScope.launch {
            try {
                val player = NetworkPlayer(id = localPlayerId, name = playerName, coins = 2, cards = emptyList()) // Initial state
                val game = apiService.createGame(player)
                _gameId.value = game.id
                gameState.updateFromNetwork(game, localPlayerId)
                startPollingGameStatus()
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to create game", e)
                _error.value = "Failed to create game: ${e.message}"
            }
        }
    }

    fun joinOnlineGame(gameId: String, playerName: String) {
        localPlayerName = playerName
        viewModelScope.launch {
            try {
                val player = NetworkPlayer(id = localPlayerId, name = playerName, coins = 2, cards = emptyList()) // Initial state
                val game = apiService.joinGame(gameId, player)
                _gameId.value = game.id
                gameState.updateFromNetwork(game, localPlayerId)
                startPollingGameStatus()
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to join game", e)
                _error.value = "Failed to join game: ${e.message}"
            }
        }
    }

    private fun startPollingGameStatus() {
        viewModelScope.launch {
            while (true) {
                val id = _gameId.value ?: break
                try {
                    val game = apiService.getGameStatus(id)
                    gameState.updateFromNetwork(game, localPlayerId)
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Failed to get game status", e)
                }
                delay(500)
            }
        }
    }

    fun startOnlineGame() {
        val id = _gameId.value ?: return
        viewModelScope.launch {
            try {
                apiService.startGame(id)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to start game", e)
                _error.value = "Failed to start game: ${e.message}"
            }
        }
    }

    fun performOnlineAction(action: Action) {
        val id = _gameId.value ?: return
        if (action == Action.COUP || action == Action.ASSASSINATE || action == Action.STEAL) {
            gameState.startTargetSelection(action)
        } else {
            viewModelScope.launch {
                try {
                    val networkAction = NetworkGameAction(player_id = localPlayerId, action = action.name)
                    apiService.performAction(id, networkAction)
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Action failed", e)
                    _error.value = "Action failed: ${e.message}"
                }
            }
        }
    }

    fun performOnlineTargetedAction(action: Action, targetPlayerName: String) {
        val id = _gameId.value ?: return
        val targetPlayer = gameState.players.value.find { it.name == targetPlayerName } ?: return

        viewModelScope.launch {
            try {
                val networkAction = NetworkGameAction(player_id = localPlayerId, action = action.name, target_player_id = targetPlayer.id)
                apiService.performAction(id, networkAction)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Targeted action failed", e)
                _error.value = "Targeted action failed: ${e.message}"
            }
        }
    }

    fun onTargetSelected(targetIndex: Int) {
        val action = gameState.targetPlayerAction.value ?: return
        val targetPlayer = gameState.players.value.getOrNull(targetIndex) ?: return
        performOnlineTargetedAction(action, targetPlayer.name)
        gameState.cancelTargetSelection()
    }

    fun challenge() {
        val id = _gameId.value ?: return
        viewModelScope.launch {
            try {
                apiService.challenge(id, ChallengeBody(challenger_id = localPlayerId))
            } catch (e: Exception) {
                Log.e("GameViewModel", "Challenge failed", e)
                _error.value = "Challenge failed: ${e.message}"
            }
        }
    }

    fun pass() {
        val id = _gameId.value ?: return
        viewModelScope.launch {
            try {
                apiService.pass(id, PassBody(player_id = localPlayerId))
            } catch (e: Exception) {
                Log.e("GameViewModel", "Pass failed", e)
                _error.value = "Pass failed: ${e.message}"
            }
        }
    }

    fun loseInfluence(card: Card) {
        val id = _gameId.value ?: return
        // Определение ID теряющего игрока происходит здесь, а не берется из gameState
        val losingPlayerId = gameState.playerLosingInfluence.value?.let {
            gameState.players.value.getOrNull(it.playerIndex)?.id
        } ?: localPlayerId // На всякий случай, если состояние не успело обновиться

        viewModelScope.launch {
            try {
                apiService.loseInfluence(id, LoseInfluenceBody(player_id = losingPlayerId, card = card.name))
            } catch (e: Exception) {
                Log.e("GameViewModel", "Lose influence failed", e)
                _error.value = "Lose influence failed: ${e.message}"
            }
        }
    }

    fun performExchange(cardsToKeep: List<Card>) {
        val id = _gameId.value ?: return
        if (gameState.exchangeState.value == null) {
            _error.value = "Cannot perform exchange right now."
            return
        }

        viewModelScope.launch {
            try {
                val body = ExchangeBody(
                    player_id = localPlayerId,
                    cards_to_keep = cardsToKeep.map { it.name }
                )
                apiService.exchangeCards(id, body)
                gameState.completeExchange()
            } catch (e: Exception) {
                Log.e("GameViewModel", "Exchange failed", e)
                _error.value = "Exchange failed: ${e.message}"
            }
        }
    }
}
