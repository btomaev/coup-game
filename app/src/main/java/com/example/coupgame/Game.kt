package com.example.coupgame

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Card(@StringRes val titleResId: Int) {
    DUKE(R.string.card_duke),
    ASSASSIN(R.string.card_assassin),
    CAPTAIN(R.string.card_captain),
    AMBASSADOR(R.string.card_ambassador),
    CONTESSA(R.string.card_contessa)
}

enum class Action(val requiredCard: Card? = null) {
    INCOME,
    FOREIGN_AID,
    TAX(Card.DUKE),
    ASSASSINATE(Card.ASSASSIN),
    STEAL(Card.CAPTAIN),
    EXCHANGE(Card.AMBASSADOR),
    COUP
}

data class Player(
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

    val deck = mutableListOf<Card>()

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

    fun startGame(playerNames: List<String>) {
        val startingPlayers = playerNames.map { Player(it) }

        deck.clear()
        deck.addAll(Card.entries.toTypedArray())
        deck.addAll(Card.entries.toTypedArray())
        deck.addAll(Card.entries.toTypedArray())
        deck.shuffle()

        val dealtPlayers = startingPlayers.map { player ->
            player.copy(cards = listOf(deck.removeAt(0), deck.removeAt(0)))
        }
        _players.value = dealtPlayers
        _currentPlayerIndex.value = 0
        addLog(R.string.log_game_started, listOf(playerNames.joinToString()))
    }

    private fun addLog(@StringRes messageResId: Int, args: List<Any> = emptyList()) {
        _gameLog.value = _gameLog.value + GameLog(messageResId, args)
    }

    private fun nextTurn() {
        var nextPlayerIndex = (_currentPlayerIndex.value + 1) % _players.value.size
        while (_players.value[nextPlayerIndex].isEliminated) {
            nextPlayerIndex = (nextPlayerIndex + 1) % _players.value.size
        }
        _currentPlayerIndex.value = nextPlayerIndex
    }

    fun performAction(action: Action, targetPlayerIndex: Int? = null) {
        val currentPlayer = _players.value[_currentPlayerIndex.value]
        if (action.requiredCard != null) {
            val potentialChallengers = _players.value.indices.filter { it != _currentPlayerIndex.value && !_players.value[it].isEliminated }
            _pendingAction.value = PendingAction(action, _currentPlayerIndex.value, targetPlayerIndex, potentialChallengers.toMutableList())
            // The action is pending, so we don't execute it right away.
            return
        }

        when (action) {
            Action.INCOME -> {
                updatePlayer(currentPlayerIndex.value, currentPlayer.copy(coins = currentPlayer.coins + 1))
                addLog(R.string.log_income, listOf(currentPlayer.name))
                nextTurn()
            }
            Action.FOREIGN_AID -> {
                addLog(R.string.log_foreign_aid, listOf(currentPlayer.name))
                // TODO: Add blocking logic
                updatePlayer(currentPlayerIndex.value, currentPlayer.copy(coins = currentPlayer.coins + 2))
                nextTurn()
            }
            Action.COUP -> {
                if (targetPlayerIndex == null) {
                    addLog(R.string.log_coup_requires_target)
                    return
                }
                if (currentPlayer.coins >= 7) {
                    updatePlayer(currentPlayerIndex.value, currentPlayer.copy(coins = currentPlayer.coins - 7))
                    addLog(R.string.log_coup, listOf(currentPlayer.name, _players.value[targetPlayerIndex].name))
                    loseInfluence(targetPlayerIndex) { nextTurn() }
                } else {
                    addLog(R.string.log_coup_insufficient_coins, listOf(currentPlayer.name))
                }
            }
            else -> {
                addLog(R.string.log_action_not_implemented)
            }
        }
    }

    fun challenge(challengerIndex: Int) {
        val pending = _pendingAction.value ?: return
        _pendingAction.value = null // The challenge is happening, so clear the pending action

        val challengedPlayerIndex = pending.playerIndex
        val challengedPlayer = _players.value[challengedPlayerIndex]
        val challengerPlayer = _players.value[challengerIndex]
        val requiredCard = pending.action.requiredCard!!

        addLog(R.string.log_challenge_issued, listOf(challengerPlayer.name, challengedPlayer.name))

        if (challengedPlayer.cards.contains(requiredCard)) {
            // Challenge failed
            addLog(R.string.log_challenge_failed_has_card, listOf(challengerPlayer.name, challengedPlayer.name, requiredCard.titleResId))
            loseInfluence(challengerIndex) {
                // The challenged player shows the card, swaps it, and continues the action
                val newCard = deck.removeAt(0)
                val oldCardIndex = challengedPlayer.cards.indexOf(requiredCard)
                val updatedCards = challengedPlayer.cards.toMutableList()
                updatedCards[oldCardIndex] = newCard
                deck.add(requiredCard)
                deck.shuffle()
                updatePlayer(challengedPlayerIndex, challengedPlayer.copy(cards = updatedCards))

                executePendingAction(pending)
                if (pending.action != Action.EXCHANGE) {
                    nextTurn()
                }
            }
        } else {
            // Challenge successful (bluff caught)
            addLog(R.string.log_challenge_successful_bluff, listOf(challengerPlayer.name, challengedPlayer.name, requiredCard.titleResId))
            loseInfluence(challengedPlayerIndex) { nextTurn() }
        }
    }

    fun pass(playerIndex: Int) {
        val pending = _pendingAction.value ?: return
        pending.potentialChallengers.remove(playerIndex)

        if (pending.potentialChallengers.isEmpty()) {
            _pendingAction.value = null
            executePendingAction(pending)
            if (pending.action != Action.EXCHANGE) {
                nextTurn()
            }
        } else {
            _pendingAction.value = pending
        }
    }

    private fun executePendingAction(pendingAction: PendingAction) {
        val currentPlayer = _players.value[pendingAction.playerIndex]
        when (pendingAction.action) {
            Action.TAX -> {
                addLog(R.string.log_tax, listOf(currentPlayer.name))
                updatePlayer(pendingAction.playerIndex, currentPlayer.copy(coins = currentPlayer.coins + 3))
            }
            Action.ASSASSINATE -> {
                if (pendingAction.targetPlayerIndex == null) return
                val targetPlayer = _players.value[pendingAction.targetPlayerIndex]
                if (currentPlayer.coins >= 3) {
                    updatePlayer(pendingAction.playerIndex, currentPlayer.copy(coins = currentPlayer.coins - 3))
                    addLog(R.string.log_assassinate_attempt, listOf(currentPlayer.name, targetPlayer.name))
                    loseInfluence(pendingAction.targetPlayerIndex) { nextTurn() }
                } else {
                     addLog(R.string.log_assassinate_insufficient_coins, listOf(currentPlayer.name))
                }
            }
            Action.STEAL -> {
                if (pendingAction.targetPlayerIndex == null) return
                val targetPlayer = _players.value[pendingAction.targetPlayerIndex]
                addLog(R.string.log_steal, listOf(currentPlayer.name, targetPlayer.name))
                val stolenCoins = targetPlayer.coins.coerceAtMost(2)
                updatePlayer(pendingAction.playerIndex, currentPlayer.copy(coins = currentPlayer.coins + stolenCoins))
                updatePlayer(pendingAction.targetPlayerIndex, targetPlayer.copy(coins = targetPlayer.coins - stolenCoins))
            }
            Action.EXCHANGE -> {
                addLog(R.string.log_exchange_ambassador, listOf(currentPlayer.name))
                val extraCards = listOf(deck.removeAt(0), deck.removeAt(0))
                val allCards = currentPlayer.cards + extraCards
                _exchangeState.value = ExchangeState(pendingAction.playerIndex, allCards)
            }
            else -> {}
        }
    }

    fun performExchange(playerIndex: Int, cardsToKeep: List<Card>) {
        val player = _players.value[playerIndex]
        val exchange = _exchangeState.value
        if (exchange == null || exchange.playerIndex != playerIndex) return

        val cardsToReturn = exchange.options.toMutableList()
        cardsToReturn.removeAll(cardsToKeep)

        deck.addAll(cardsToReturn)
        deck.shuffle()

        updatePlayer(playerIndex, player.copy(cards = cardsToKeep))
        addLog(R.string.log_exchange_cards, listOf(player.name))
        _exchangeState.value = null
        nextTurn()
    }

    fun cancelExchange() {
        val exchange = _exchangeState.value ?: return
        val player = _players.value[exchange.playerIndex]
        val originalCards = player.cards
        val drawnCards = exchange.options.filterNot { originalCards.contains(it) }
        deck.addAll(drawnCards)
        deck.shuffle()
        _exchangeState.value = null
        nextTurn() // Or should we just go back?
    }


    private fun loseInfluence(playerIndex: Int, andThen: () -> Unit) {
        val player = _players.value[playerIndex]
        if (player.cards.size > 1) {
            _playerLosingInfluence.value = PlayerLosingInfluence(playerIndex, andThen)
        } else if (player.cards.isNotEmpty()) {
            confirmLoseInfluence(player.cards.first(), andThen)
        }
    }

    fun confirmLoseInfluence(card: Card, andThen: (() -> Unit)? = null) {
        val losingInfluenceState = _playerLosingInfluence.value
        val playerIndex = losingInfluenceState?.playerIndex ?: _players.value.indexOfFirst { it.cards.contains(card) } // Fallback, not ideal

        val player = _players.value[playerIndex]
        val remainingCards = player.cards.toMutableList()
        remainingCards.remove(card)

        addLog(R.string.log_lose_influence, listOf(player.name, card.titleResId))

        val updatedPlayer = player.copy(cards = remainingCards)
        updatePlayer(playerIndex, updatedPlayer)

        if (updatedPlayer.cards.isEmpty()) {
            eliminatePlayer(playerIndex)
        }

        _playerLosingInfluence.value = null
        (andThen ?: losingInfluenceState?.continuation)?.invoke()
    }


    private fun eliminatePlayer(playerIndex: Int) {
        val player = _players.value[playerIndex]
        addLog(R.string.log_player_eliminated, listOf(player.name))
        updatePlayer(playerIndex, player.copy(isEliminated = true))
    }

    private fun updatePlayer(index: Int, player: Player) {
        val updatedPlayers = _players.value.toMutableList()
        updatedPlayers[index] = player
        _players.value = updatedPlayers
    }

    fun startTargetSelection(action: Action) {
        _targetPlayerAction.value = action
    }

    fun cancelTargetSelection() {
        _targetPlayerAction.value = null
    }

    fun onTargetSelected(targetIndex: Int) {
        val action = _targetPlayerAction.value
        _targetPlayerAction.value = null
        if (action != null) {
            performAction(action, targetIndex)
        }
    }
}
