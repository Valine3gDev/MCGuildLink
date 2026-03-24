package io.github.valine3gdev.mcguildlink.app.util

import dev.kord.core.entity.User
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import kotlin.uuid.Uuid


suspend fun AccountLinkService.getOrCreateLinkRequest(user: User) =
    getOrCreateLinkRequest(user.id.value, user.username)

suspend fun AccountLinkService.getLinkedMinecraftAccounts(user: User) =
    getLinkedMinecraftAccounts(user.id.value)

suspend fun AccountLinkService.getLinkOrNull(user: User, minecraftUuid: Uuid) =
    getLinkOrNull(user.id.value, minecraftUuid)

suspend fun AccountLinkService.unlink(user: User, minecraftUuid: Uuid) =
    unlink(user.id.value, minecraftUuid)

suspend fun AccountLinkService.unlinkByDiscord(user: User) =
    unlinkByDiscord(user.id.value)
