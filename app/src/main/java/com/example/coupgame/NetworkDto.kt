package com.example.coupgame

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkPlayer(
    val id: String,
    val name: String,
    val coins: Int,
    val cards: List<String>,
    val is_eliminated: Boolean = false,
    val must_lose_influence: Boolean = false
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkGame(
    val id: String,
    val players: List<NetworkPlayer>,
    val state: String,
    val current_player_id: String? = null,
    val pending_action: PendingActionNet? = null,
    val exchange_state: ExchangeStateNet? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PendingActionNet(
    val action: String,
    val player_id: String,
    val target_player_id: String?,
    val potential_challengers: List<String>
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ExchangeStateNet(
    val player_id: String,
    val options: List<String>
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkGameAction(
    val player_id: String,
    val action: String,
    val target_player_id: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ChallengeBody(
    val challenger_id: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PassBody(
    val player_id: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LoseInfluenceBody(
    val player_id: String,
    val card: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ExchangeBody(
    val player_id: String,
    val cards_to_keep: List<String>
)
