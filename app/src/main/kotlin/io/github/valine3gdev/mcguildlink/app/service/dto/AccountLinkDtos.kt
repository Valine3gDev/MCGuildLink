package io.github.valine3gdev.mcguildlink.app.service.dto

import kotlin.uuid.Uuid


data class LinkRequestResult(
    val code: String
)


sealed interface LinkResult {
    data class Success(val discordAccount: DiscordAccountInfo) : LinkResult

    object InvalidCode : LinkResult

    object AlreadyLinked : LinkResult
}


data class DiscordAccountInfo(
    val userId: ULong,
    val lastKnownUsername: String,
)


data class MinecraftAccountInfo(
    val uuid: Uuid,
    val lastKnownName: String,
)
