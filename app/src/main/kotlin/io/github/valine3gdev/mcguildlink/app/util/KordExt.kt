package io.github.valine3gdev.mcguildlink.app.util

import dev.kord.core.entity.User
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import kotlin.uuid.Uuid


suspend fun AccountLinkService.getOrCreateLinkRequest(user: User) =
    getOrCreateLinkRequest(user.id.value, user.username)

suspend fun AccountLinkService.listLinkedMinecraftAccounts(user: User) =
    listLinkedMinecraftAccounts(user.id.value)

suspend fun AccountLinkService.unlink(user: User, minecraftUuid: Uuid) =
    unlink(user.id.value, minecraftUuid)
