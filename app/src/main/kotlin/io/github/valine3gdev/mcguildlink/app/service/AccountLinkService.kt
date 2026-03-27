package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.AccountLinkEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequestEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequests
import io.github.valine3gdev.mcguildlink.app.service.dto.DiscordAccountInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkRequestResult
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkResult
import io.github.valine3gdev.mcguildlink.app.service.dto.MinecraftAccountInfo
import io.github.valine3gdev.mcguildlink.app.util.LinkCodeGenerator
import io.github.valine3gdev.mcguildlink.app.util.RandomLinkCodeGenerator
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.uuid.Uuid


class AccountLinkService(
    private val db: Database,
    private val codeGenerator: LinkCodeGenerator = RandomLinkCodeGenerator()
) {
    suspend fun getOrCreateLinkRequest(discordUserId: ULong, username: String) = suspendTransaction(db) {
        val discord = AccountStore.getOrCreateDiscordAccount(discordUserId, username)
        if (discord.isBlocked()) {
            return@suspendTransaction LinkRequestResult.Blocked
        }

        val request = discord.linkRequest ?: LinkRequestEntity.new {
            discordAccount = discord
            code = generateUniqueCode()
        }

        LinkRequestResult.Success(request.code)
    }

    fun consumeCodeAndLink(code: String, uuid: Uuid, name: String) = transaction(db) {
        val request = LinkRequestEntity.find {
            LinkRequests.code eq code
        }.firstOrNull() ?: return@transaction LinkResult.InvalidCode

        val discord = request.discordAccount
        if (discord.isBlocked()) {
            return@transaction LinkResult.Blocked
        }

        val minecraft = AccountStore.getOrCreateMinecraftAccount(uuid, name)
        if (minecraft.isBlocked()) {
            return@transaction LinkResult.Blocked
        }

        AccountStore.getAccountLinkOrNull(discord, minecraft)
            ?.let {
                return@transaction LinkResult.AlreadyLinked
            }
            ?: AccountLinkEntity.new {
                discordAccount = discord
                minecraftAccount = minecraft
                linkedAt = Instant.now()
            }

        request.delete()

        LinkResult.Success(DiscordAccountInfo(discord.userId, discord.lastKnownUsername))
    }

    suspend fun getLinkedMinecraftAccounts(userId: ULong): List<MinecraftAccountInfo> = suspendTransaction(db) {
        val discord = AccountStore.getDiscordAccountOrNull(userId) ?: return@suspendTransaction emptyList()
        discord.links.map { link ->
            val mc = link.minecraftAccount
            MinecraftAccountInfo(mc.uuid, mc.lastKnownName)
        }
    }

    suspend fun getLinkedDiscordAccounts(uuid: Uuid): List<DiscordAccountInfo> = suspendTransaction(db) {
        val minecraft = AccountStore.getMinecraftAccountOrNull(uuid) ?: return@suspendTransaction emptyList()
        minecraft.links.map { link ->
            val dc = link.discordAccount
            DiscordAccountInfo(dc.userId, dc.lastKnownUsername)
        }
    }

    suspend fun getLinkOrNull(discordUserId: ULong, minecraftUuid: Uuid) = suspendTransaction(db) {
        val link = AccountStore.getAccountLinkOrNull(discordUserId, minecraftUuid) ?: return@suspendTransaction null
        val discord = link.discordAccount
        val minecraft = link.minecraftAccount
        Pair(
            DiscordAccountInfo(discord.userId, discord.lastKnownUsername),
            MinecraftAccountInfo(minecraft.uuid, minecraft.lastKnownName)
        )
    }

    suspend fun unlink(discordUserId: ULong, minecraftUuid: Uuid) = suspendTransaction(db) {
        val link = AccountStore.getAccountLinkOrNull(discordUserId, minecraftUuid) ?: return@suspendTransaction false
        link.delete()
        true
    }

    suspend fun unlinkByDiscord(discordUserId: ULong) = suspendTransaction(db) {
        val discord = AccountStore.getDiscordAccountOrNull(discordUserId) ?: return@suspendTransaction false
        discord.links.forEach { it.delete() }
    }

    private fun generateUniqueCode(): String {
        repeat(20) {
            val code = codeGenerator.generate()
            val isUnique = LinkRequestEntity.find {
                LinkRequests.code eq code
            }.empty()
            if (isUnique) return code
        }
        error("Failed to generate unique link code")
    }
}
