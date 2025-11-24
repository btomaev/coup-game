package com.example.coupgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.coupgame.ui.theme.CoupGameTheme

class GameViewModelFactory(private val playerNames: List<String>) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(playerNames) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GameViewModel(playerNames: List<String>) : ViewModel() {
    val gameState = GameState()

    init {
        gameState.startGame(playerNames)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playerNames = listOf(
            getString(R.string.player_1),
            getString(R.string.player_2),
            getString(R.string.player_3)
        )
        val gameViewModel: GameViewModel by viewModels { GameViewModelFactory(playerNames) }

        setContent {
            CoupGameTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameScreen(
                        gameState = gameViewModel.gameState,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun GameScreen(gameState: GameState, modifier: Modifier = Modifier) {
    val players by gameState.players.collectAsState()
    val currentPlayerIndex by gameState.currentPlayerIndex.collectAsState()
    val gameLog by gameState.gameLog.collectAsState()
    val targetPlayerAction by gameState.targetPlayerAction.collectAsState()
    val pendingAction by gameState.pendingAction.collectAsState()
    val exchangeState by gameState.exchangeState.collectAsState()
    val playerLosingInfluence by gameState.playerLosingInfluence.collectAsState()

    val currentPlayer = players.getOrNull(currentPlayerIndex)

    if (targetPlayerAction != null) {
        TargetSelectionDialog(
            players = players,
            currentPlayerIndex = currentPlayerIndex,
            onTargetSelected = { targetIndex -> gameState.onTargetSelected(targetIndex) },
            onDismiss = { gameState.cancelTargetSelection() }
        )
    }

    val currentExchangeState = exchangeState
    if (currentExchangeState != null) {
        ExchangeCardsDialog(
            exchangeState = currentExchangeState,
            onCardsSelected = { cards -> gameState.performExchange(currentExchangeState.playerIndex, cards) },
            onDismiss = { gameState.cancelExchange() }
        )
    }

    val currentLosingInfluence = playerLosingInfluence
    if (currentLosingInfluence != null) {
        LoseInfluenceDialog(
            player = players[currentLosingInfluence.playerIndex],
            onCardSelected = { card -> gameState.confirmLoseInfluence(card) }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        PlayerStatus(players, currentPlayerIndex)
        Spacer(Modifier.height(16.dp))

        val currentPendingAction = pendingAction
        if (currentPendingAction != null) {
            ChallengePhase(
                pendingAction = currentPendingAction,
                players = players,
                gameState = gameState
            )
        } else {
            if (currentPlayer != null && !currentPlayer.isEliminated && currentExchangeState == null && currentLosingInfluence == null) {
                AvailableActions(
                    gameState = gameState,
                    currentPlayer = currentPlayer,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        GameLogPanel(gameLog)
    }
}

@Composable
fun PlayerStatus(players: List<Player>, currentPlayerIndex: Int) {
    Column {
        Text(stringResource(R.string.players), style = MaterialTheme.typography.headlineSmall)
        val coinsString = stringResource(R.string.coins)
        val influenceString = stringResource(R.string.influence)
        players.forEachIndexed { index, player ->
            val isCurrent = index == currentPlayerIndex
            val playerText = "${if (isCurrent) stringResource(id = R.string.current_player_indicator) else ""}${player.name}: ${player.coins} $coinsString, ${player.cards.size} $influenceString"
            Text(playerText, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvailableActions(gameState: GameState, currentPlayer: Player) {
    Column {
        Text(stringResource(R.string.your_turn, currentPlayer.name), style = MaterialTheme.typography.headlineSmall)
        val cardTitles = currentPlayer.cards
            .map { stringResource(it.titleResId) }
            .joinToString()
        Text(stringResource(R.string.your_cards, cardTitles))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.actions), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { gameState.performAction(Action.INCOME) }) { Text(stringResource(R.string.income)) }
            Button(onClick = { gameState.performAction(Action.FOREIGN_AID) }) { Text(stringResource(R.string.foreign_aid)) }
            Button(onClick = { gameState.performAction(Action.TAX) }) { Text(stringResource(R.string.tax_duke)) }
            Button(onClick = { gameState.startTargetSelection(Action.COUP) }, enabled = currentPlayer.coins >= 7) { Text(stringResource(R.string.coup_coins, 7)) }
            Button(onClick = { gameState.startTargetSelection(Action.ASSASSINATE) }, enabled = currentPlayer.coins >= 3) { Text(stringResource(R.string.assassinate_coins, 3)) }
            Button(onClick = { gameState.startTargetSelection(Action.STEAL) }) { Text(stringResource(R.string.steal_captain)) }
            Button(onClick = { gameState.performAction(Action.EXCHANGE) }) { Text(stringResource(R.string.exchange_ambassador)) }
        }
    }
}

@Composable
fun ChallengePhase(
    pendingAction: PendingAction,
    players: List<Player>,
    gameState: GameState
) {
    val actionPlayer = players[pendingAction.playerIndex]
    val action = pendingAction.action
    val targetPlayer = pendingAction.targetPlayerIndex?.let { players[it] }

    val actionText = when (action) {
        Action.TAX -> stringResource(R.string.log_tax, actionPlayer.name)
        Action.ASSASSINATE -> if (targetPlayer != null) stringResource(R.string.log_assassinate_attempt, actionPlayer.name, targetPlayer.name) else ""
        Action.STEAL -> if (targetPlayer != null) stringResource(R.string.log_steal, actionPlayer.name, targetPlayer.name) else ""
        Action.EXCHANGE -> stringResource(R.string.log_exchange_ambassador, actionPlayer.name)
        else -> ""
    }

    Column {
        Text(text = stringResource(id = R.string.pending_action, actionText), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(id = R.string.challenge_or_pass))
        Spacer(modifier = Modifier.height(8.dp))

        pendingAction.potentialChallengers.forEach { challengerIndex ->
            val challenger = players[challengerIndex]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(id = R.string.challenger_prompt, challenger.name))
                Button(onClick = { gameState.challenge(challengerIndex) }) {
                    Text(stringResource(R.string.challenge))
                }
                Button(onClick = { gameState.pass(challengerIndex) }) {
                    Text(stringResource(R.string.pass))
                }
            }
        }
    }
}

@Composable
fun GameLogPanel(log: List<GameLog>) {
    Column {
        Text(stringResource(R.string.game_log), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            items(log.reversed()) { logEntry ->
                val formattedArgs = logEntry.args.map {
                    if (it is Int) stringResource(id = it) else it
                }.toTypedArray()
                Text(stringResource(logEntry.messageResId, *formattedArgs))
            }
        }
    }
}

@Composable
fun TargetSelectionDialog(players: List<Player>, currentPlayerIndex: Int, onTargetSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_target)) },
        text = {
            Column {
                players.forEachIndexed { index, player ->
                    if (index != currentPlayerIndex && !player.isEliminated) {
                        Button(onClick = { onTargetSelected(index) }) {
                            Text(player.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeCardsDialog(exchangeState: ExchangeState, onCardsSelected: (List<Card>) -> Unit, onDismiss: () -> Unit) {
    var selectedCards by remember { mutableStateOf<List<Card>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exchange_cards_title)) },
        text = {
            Column {
                Text(stringResource(R.string.exchange_cards_prompt))
                Spacer(Modifier.height(8.dp))
                exchangeState.options.forEach { card ->
                    val isSelected = selectedCards.contains(card)
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { 
                                selectedCards = if (isSelected) {
                                    selectedCards - card
                                } else {
                                    if (selectedCards.size < 2) {
                                        selectedCards + card
                                    } else {
                                        selectedCards
                                    }
                                }
                            }
                        )
                        Text(stringResource(card.titleResId))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCardsSelected(selectedCards) },
                enabled = selectedCards.size == 2
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}


@Composable
fun LoseInfluenceDialog(player: Player, onCardSelected: (Card) -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Cannot be dismissed */ },
        title = { Text(stringResource(R.string.lose_influence_title)) },
        text = {
            Column {
                Text(stringResource(R.string.lose_influence_prompt))
                Spacer(Modifier.height(8.dp))
                player.cards.forEach { card ->
                    Button(onClick = { onCardSelected(card) }) {
                        Text(stringResource(card.titleResId))
                    }
                }
            }
        },
        confirmButton = {}
    )
}
