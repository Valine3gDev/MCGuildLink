package io.github.valine3gdev.mcguildlink.app.service.dto

import java.time.Instant
import kotlin.uuid.Uuid


sealed interface LinkRequestResult {
    data class Success(val code: String) : LinkRequestResult

    data object Blocked : LinkRequestResult
}


sealed interface LinkResult {
    data class Success(val discordAccount: DiscordAccountInfo) : LinkResult

    data object InvalidCode : LinkResult

    data object AlreadyLinked : LinkResult

    data object Blocked : LinkResult
}


sealed interface BlockResult {
    data class Success(
        val rootDiscordAccount: DiscordAccountInfo,
        val blockedDiscordAccountInfos: List<DiscordAccountInfo>,
        val blockedMinecraftAccountInfos: List<MinecraftAccountInfo>,
    ) : BlockResult

    data object AlreadyBlocked : BlockResult
}


sealed interface UnblockResult {
    data class Success(val blockGroup: BlockedAccountGroupInfo) : UnblockResult

    data object NotBlocked : UnblockResult
}


data class BlockedAccountGroupInfo(
    val rootDiscordAccount: DiscordAccountInfo,
    val blockedDiscordAccounts: Int,
    val blockedMinecraftAccounts: Int,
    val createdAt: Instant,
)


data class AccountLinkSummary(
    val discordAccount: DiscordAccountInfo,
    val minecraftAccount: MinecraftAccountInfo,
    val linkedAt: Instant,
)


data class DiscordAccountInfo(
    val userId: ULong,
    val lastKnownUsername: String,
)


data class MinecraftAccountInfo(
    val uuid: Uuid,
    val lastKnownName: String,
)

fun MinecraftAccountInfo.toHeadAvatarUrl() = "https://mc-heads.net/avatar/$uuid"
