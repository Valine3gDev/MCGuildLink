package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.AccountLinkEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequestEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequests
import io.github.valine3gdev.mcguildlink.app.service.dto.AccountLinkSummary
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
import kotlin.collections.forEach
import kotlin.uuid.Uuid


class AccountLinkService(
    private val db: Database,
    private val codeGenerator: LinkCodeGenerator = RandomLinkCodeGenerator(),
    private val whitelistRefreshRequester: WhitelistRefreshRequester = NoopWhitelistRefreshRequester,
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

    fun consumeCodeAndLink(code: String, uuid: Uuid, name: String): LinkResult {
        val result = transaction(db) {
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

        if (result is LinkResult.Success) {
            whitelistRefreshRequester.requestRefresh()
        }

        return result
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

    suspend fun listLinksByDiscord(discordUserId: ULong): List<AccountLinkSummary> = suspendTransaction(db) {
        val discord = AccountStore.getDiscordAccountOrNull(discordUserId) ?: return@suspendTransaction emptyList()
        discord.links
            .map { it.toSummary() }
            .sortedByDescending { it.linkedAt }
    }

    suspend fun listLinksByMinecraft(uuid: Uuid): List<AccountLinkSummary> = suspendTransaction(db) {
        val minecraft = AccountStore.getMinecraftAccountOrNull(uuid) ?: return@suspendTransaction emptyList()
        minecraft.links
            .map { it.toSummary() }
            .sortedByDescending { it.linkedAt }
    }

    suspend fun listAllLinks(): List<AccountLinkSummary> = suspendTransaction(db) {
        AccountLinkEntity.all()
            .map { it.toSummary() }
            .sortedByDescending { it.linkedAt }
    }

    suspend fun unlink(discordUserId: ULong, minecraftUuid: Uuid): Boolean {
        val removed = suspendTransaction(db) {
            val link =
                AccountStore.getAccountLinkOrNull(discordUserId, minecraftUuid) ?: return@suspendTransaction false
            link.delete()
            true
        }

        if (removed) {
            whitelistRefreshRequester.requestRefresh()
        }

        return removed
    }

    suspend fun unlinkByDiscord(discordUserId: ULong): List<MinecraftAccountInfo> = suspendTransaction(db) {
        val discord = AccountStore.getDiscordAccountOrNull(discordUserId) ?: return@suspendTransaction null
        val links = discord.links.toList()
        if (links.isEmpty()) {
            return@suspendTransaction null
        }

        buildList<MinecraftAccountInfo>(links.size) {
            links.forEach { link ->
                val mc = link.minecraftAccount
                add(MinecraftAccountInfo(mc.uuid, mc.lastKnownName))
                link.delete()
            }
        }
    }?.also {
        whitelistRefreshRequester.requestRefresh()
    } ?: emptyList()

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

private fun AccountLinkEntity.toSummary(): AccountLinkSummary {
    val discord = discordAccount
    val minecraft = minecraftAccount

    return AccountLinkSummary(
        discordAccount = DiscordAccountInfo(discord.userId, discord.lastKnownUsername),
        minecraftAccount = MinecraftAccountInfo(minecraft.uuid, minecraft.lastKnownName),
        linkedAt = linkedAt,
    )
}
