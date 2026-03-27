package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.AccountLinkEntity
import io.github.valine3gdev.mcguildlink.app.db.AccountLinks
import io.github.valine3gdev.mcguildlink.app.db.DiscordAccountEntity
import io.github.valine3gdev.mcguildlink.app.db.DiscordAccounts
import io.github.valine3gdev.mcguildlink.app.db.MinecraftAccountEntity
import io.github.valine3gdev.mcguildlink.app.db.MinecraftAccounts
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlin.uuid.Uuid


internal object AccountStore {
    fun getDiscordAccountOrNull(userId: ULong) = DiscordAccountEntity.find {
        DiscordAccounts.userId eq userId
    }.firstOrNull()

    fun getMinecraftAccountOrNull(uuid: Uuid) = MinecraftAccountEntity.find {
        MinecraftAccounts.uuid eq uuid
    }.firstOrNull()

    fun getOrCreateDiscordAccount(discordUserId: ULong, username: String) =
        getDiscordAccountOrNull(discordUserId)?.apply {
            lastKnownUsername = username
        } ?: DiscordAccountEntity.new {
            this.userId = discordUserId
            this.lastKnownUsername = username
        }

    fun getOrCreateMinecraftAccount(uuid: Uuid, name: String) =
        getMinecraftAccountOrNull(uuid)?.apply {
            lastKnownName = name
        } ?: MinecraftAccountEntity.new {
            this.uuid = uuid
            this.lastKnownName = name
        }

    fun getAccountLinkOrNull(discord: DiscordAccountEntity, minecraft: MinecraftAccountEntity) =
        AccountLinkEntity.find {
            (AccountLinks.discordAccount eq discord.id) and (AccountLinks.minecraftAccount eq minecraft.id)
        }.firstOrNull()

    fun getAccountLinkOrNull(discordUserId: ULong, minecraftUuid: Uuid): AccountLinkEntity? {
        val discord = getDiscordAccountOrNull(discordUserId) ?: return null
        val minecraft = getMinecraftAccountOrNull(minecraftUuid) ?: return null
        return getAccountLinkOrNull(discord, minecraft)
    }
}


internal fun DiscordAccountEntity.isBlocked() = blockedMembership != null

internal fun MinecraftAccountEntity.isBlocked() = blockedMembership != null
