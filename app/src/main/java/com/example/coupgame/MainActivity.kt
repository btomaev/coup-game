package com.example.coupgame

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupgame.ui.theme.CoupGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gameViewModel: GameViewModel by viewModels()

        setContent {
            CoupGameTheme {
                val gameId by gameViewModel.gameId.collectAsState()
                val error by gameViewModel.error.collectAsState()
                val gameStatus by gameViewModel.gameState.gameStatus.collectAsState()

                LaunchedEffect(error) {
                    error?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (gameId == null) {
                        LobbyScreen(
                            viewModel = gameViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        when (gameStatus) {
                            "waiting" -> WaitingRoomScreen(
                                viewModel = gameViewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                            "in_progress" -> GameScreen(
                                viewModel = gameViewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LobbyScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    var playerName by remember { mutableStateOf("") }
    var gameIdInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Coup Online", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text("Your Name") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = { viewModel.createOnlineGame(playerName) }, enabled = playerName.isNotBlank()) {
            Text("Create Game")
        }

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = gameIdInput,
            onValueChange = { gameIdInput = it },
            label = { Text("Game ID") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = { viewModel.joinOnlineGame(gameIdInput, playerName) }, enabled = playerName.isNotBlank() && gameIdInput.isNotBlank()) {
            Text("Join Game")
        }
    }
}

@Composable
fun WaitingRoomScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val players by viewModel.gameState.players.collectAsState()
    val gameId by viewModel.gameId.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Game Lobby", style = MaterialTheme.typography.headlineMedium)
        Text("Game ID: $gameId")
        Spacer(Modifier.height(32.dp))

        Text("Players:", style = MaterialTheme.typography.headlineSmall)
        players.forEach {
            Text(it.name, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.startOnlineGame() },
            enabled = players.size >= 2
        ) {
            Text("Start Game")
        }
    }
}


@Composable
fun GameScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val gameState = viewModel.gameState
    val players by gameState.players.collectAsState()
    val currentPlayerIndex by gameState.currentPlayerIndex.collectAsState()
    val gameLog by gameState.gameLog.collectAsState()
    val targetPlayerAction by gameState.targetPlayerAction.collectAsState()
    val pendingAction by gameState.pendingAction.collectAsState()
    val exchangeState by gameState.exchangeState.collectAsState()
    val playerLosingInfluence by gameState.playerLosingInfluence.collectAsState()

    val currentPlayer = players.getOrNull(currentPlayerIndex)
    val localPlayer = players.find { it.id == viewModel.localPlayerId }

    if (targetPlayerAction != null) {
        TargetSelectionDialog(
            players = players,
            currentPlayerIndex = currentPlayerIndex,
            onTargetSelected = { targetIndex -> viewModel.onTargetSelected(targetIndex) },
            onDismiss = { gameState.cancelTargetSelection() }
        )
    }

    val currentExchangeState = exchangeState
    if (currentExchangeState != null) {
        ExchangeCardsDialog(
            exchangeState = currentExchangeState,
            onCardsSelected = { cards -> viewModel.performExchange(cards) },
            onDismiss = { viewModel.performExchange(currentPlayer?.cards ?: emptyList()) }
        )
    }

    val currentLosingInfluence = playerLosingInfluence
    if (currentLosingInfluence != null) {
        val losingPlayer = players[currentLosingInfluence.playerIndex]
        if (losingPlayer.id == viewModel.localPlayerId) {
            LoseInfluenceDialog(
                player = losingPlayer,
                onCardSelected = { card -> viewModel.loseInfluence(card) }
            )
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        PlayerStatus(players, currentPlayerIndex)
        Spacer(Modifier.height(16.dp))

        val currentPendingAction = pendingAction
        if (currentPendingAction != null) {
            ChallengePhase(
                pendingAction = currentPendingAction,
                players = players,
                viewModel = viewModel
            )
        } else {
            if (currentPlayer != null && localPlayer != null && !currentPlayer.isEliminated && currentExchangeState == null && currentLosingInfluence == null) {
                AvailableActions(
                    viewModel = viewModel,
                    currentPlayer = currentPlayer,
                    localPlayer = localPlayer
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        GameLogPanel(gameLog)
    }
}

@Composable
fun PlayerUi(player: Player, isCurrent: Boolean) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .border(
                width = 2.dp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = player.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "Coins: ${player.coins}")
            Text(text = "Influence: ${player.cards.size}")
        }
    }
}

@Composable
fun PlayerStatus(players: List<Player>, currentPlayerIndex: Int) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        itemsIndexed(players) { index, player ->
            PlayerUi(player = player, isCurrent = index == currentPlayerIndex)
        }
    }
}

@Composable
fun ActionCard(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(120.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvailableActions(viewModel: GameViewModel, currentPlayer: Player, localPlayer: Player) {
    val isMyTurn = viewModel.localPlayerId == currentPlayer.id

    Column {
        val cardTitles = localPlayer.cards
            .map { stringResource(it.titleResId) }
            .joinToString()
        Text(stringResource(R.string.your_cards, cardTitles))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.actions), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard(
                text = stringResource(R.string.income),
                icon = Icons.Default.CheckCircle,
                onClick = { viewModel.performOnlineAction(Action.INCOME) },
                enabled = isMyTurn
            )
            ActionCard(
                text = stringResource(R.string.foreign_aid),
                icon = Icons.Default.Add,
                onClick = { viewModel.performOnlineAction(Action.FOREIGN_AID) },
                enabled = isMyTurn
            )
            ActionCard(
                text = stringResource(R.string.tax_duke),
                icon = Icons.Default.ShoppingCart,
                onClick = { viewModel.performOnlineAction(Action.TAX) },
                enabled = isMyTurn
            )
            ActionCard(
                text = stringResource(R.string.coup_coins, 7),
                icon = Icons.Default.Refresh,
                onClick = { viewModel.performOnlineAction(Action.COUP) },
                enabled = isMyTurn && currentPlayer.coins >= 7
            )
            ActionCard(
                text = stringResource(R.string.assassinate_coins, 3),
                icon = Icons.Default.Close,
                onClick = { viewModel.performOnlineAction(Action.ASSASSINATE) },
                enabled = isMyTurn && currentPlayer.coins >= 3
            )
            ActionCard(
                text = stringResource(R.string.steal_captain),
                icon = Icons.Default.Star,
                onClick = { viewModel.performOnlineAction(Action.STEAL) },
                enabled = isMyTurn
            )
            ActionCard(
                text = stringResource(R.string.exchange_ambassador),
                icon = Icons.Default.Share,
                onClick = { viewModel.performOnlineAction(Action.EXCHANGE) },
                enabled = isMyTurn
            )
        }
    }
}

@Composable
fun ChallengePhase(
    pendingAction: PendingAction,
    players: List<Player>,
    viewModel: GameViewModel
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

    val localPlayerIndex = players.indexOfFirst { it.id == viewModel.localPlayerId }
    val canChallenge = pendingAction.potentialChallengers.contains(localPlayerIndex)

    Column {
        Text(text = stringResource(id = R.string.pending_action, actionText), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(id = R.string.challenge_or_pass))
        Spacer(modifier = Modifier.height(8.dp))

        if (canChallenge) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.challenge() }) {
                    Text(stringResource(R.string.challenge))
                }
                Button(onClick = { viewModel.pass() }) {
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
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)) {
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
