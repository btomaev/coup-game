package com.example.coupgame

import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Card(@StringRes val titleResId: Int) {
    DUKE(R.string.card_duke),
    ASSASSIN(R.string.card_assassin),
    CAPTAIN(R.string.card_captain),
    AMBASSADOR(R.string.card_ambassador),
    CONTESSA(R.string.card_contessa);

    companion object {
        fun fromString(name: String): Card? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

enum class Action() {
    INCOME,
    FOREIGN_AID,
    TAX,
    ASSASSINATE,
    STEAL,
    EXCHANGE,
    COUP
}

data class Player(
    val id: String,
    val name: String,
    val coins: Int = 2,
    val cards: List<Card> = emptyList(),
    val isEliminated: Boolean = false
)

data class GameLog(@StringRes val messageResId: Int, val args: List<Any> = emptyList())

data class PendingAction(
    val action: Action,
    val playerIndex: Int,
    val targetPlayerIndex: Int?,
    val potentialChallengers: MutableList<Int>
)

data class ExchangeState(
    val playerIndex: Int,
    val options: List<Card>
)

data class PlayerLosingInfluence(
    val playerIndex: Int,
    val continuation: () -> Unit
)

class GameState {
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players

    private val _gameStatus = MutableStateFlow("waiting")
    val gameStatus: StateFlow<String> = _gameStatus

    private val _currentPlayerIndex = MutableStateFlow(0)
    val currentPlayerIndex: StateFlow<Int> = _currentPlayerIndex

    private val _gameLog = MutableStateFlow<List<GameLog>>(emptyList())
    val gameLog: StateFlow<List<GameLog>> = _gameLog

    private val _targetPlayerAction = MutableStateFlow<Action?>(null)
    val targetPlayerAction: StateFlow<Action?> = _targetPlayerAction

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction

    private val _exchangeState = MutableStateFlow<ExchangeState?>(null)
    val exchangeState: StateFlow<ExchangeState?> = _exchangeState

    private val _playerLosingInfluence = MutableStateFlow<PlayerLosingInfluence?>(null)
    val playerLosingInfluence: StateFlow<PlayerLosingInfluence?> = _playerLosingInfluence

    fun updateFromNetwork(networkGame: NetworkGame, localPlayerId: String) {
        Log.i("NetworkUpdate", "$networkGame")
        val networkPlayers = networkGame.players.map { netPlayer ->
            Player(
                id = netPlayer.id,
                name = netPlayer.name,
                coins = netPlayer.coins,
                cards = netPlayer.cards.mapNotNull { Card.fromString(it) },
                isEliminated = netPlayer.is_eliminated
            )
        }
        _players.value = networkPlayers
        _gameStatus.value = networkGame.state
        _pendingAction.value = networkGame.pending_action?.let { netPending ->
            PendingAction(
                action = Action.valueOf(netPending.action),
                playerIndex = _players.value.indexOfFirst { it.id == netPending.player_id },
                targetPlayerIndex = netPending.target_player_id?.let { id -> _players.value.indexOfFirst { it.id == id } },
                potentialChallengers = netPending.potential_challengers.map { id ->
                    _players.value.indexOfFirst { it.id == id }
                }.toMutableList()
            )
        }

        val newCurrentPlayerIndex = networkGame.current_player_id?.let { id ->
            networkPlayers.indexOfFirst { it.id == id }
        } ?: -1
        if (newCurrentPlayerIndex != -1) {
            _currentPlayerIndex.value = newCurrentPlayerIndex
        }

        val losingInfluencePlayer = networkGame.players.find { it.must_lose_influence }
        if (losingInfluencePlayer != null) {
            val playerIndex = _players.value.indexOfFirst { it.id == losingInfluencePlayer.id }
            if (playerIndex != -1) {
                _playerLosingInfluence.value = PlayerLosingInfluence(playerIndex) {}
            }
        } else {
            _playerLosingInfluence.value = null
        }

        if (networkGame.exchange_state != null) {
            val playerIndex = _players.value.indexOfFirst { it.id == networkGame.exchange_state.player_id }
            if (playerIndex != -1 && networkGame.exchange_state.player_id == localPlayerId) {
                _exchangeState.value = ExchangeState(
                    playerIndex,
                    networkGame.exchange_state.options.mapNotNull { Card.fromString(it) }
                )
            }
        } else {
            _exchangeState.value = null
        }
    }
    fun completeExchange() {
        _exchangeState.value = null
    }

    private fun addLog(@StringRes messageResId: Int, args: List<Any> = emptyList()) {
        _gameLog.value = _gameLog.value + GameLog(messageResId, args)
    }

    fun startTargetSelection(action: Action) {
        _targetPlayerAction.value = action
    }

    fun cancelTargetSelection() {
        _targetPlayerAction.value = null
    }
}
