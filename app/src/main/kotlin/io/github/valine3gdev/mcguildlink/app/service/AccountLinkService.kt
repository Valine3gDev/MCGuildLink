package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.*
import io.github.valine3gdev.mcguildlink.app.service.dto.DiscordAccountInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkRequestResult
import io.github.valine3gdev.mcguildlink.app.service.dto.MinecraftAccountInfo
import io.github.valine3gdev.mcguildlink.app.util.LinkCodeGenerator
import io.github.valine3gdev.mcguildlink.app.util.RandomLinkCodeGenerator
import org.jetbrains.exposed.v1.core.and
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
    private fun getDiscordAccountOrNull(userId: ULong) = DiscordAccountEntity.find {
        DiscordAccounts.userId eq userId
    }.firstOrNull()

    private fun getMinecraftAccountOrNull(uuid: Uuid) = MinecraftAccountEntity.find {
        MinecraftAccounts.uuid eq uuid
    }.firstOrNull()

    private fun getAccountLinkOrNull(discord: DiscordAccountEntity, minecraft: MinecraftAccountEntity) =
        AccountLinkEntity.find {
            (AccountLinks.discordAccount eq discord.id) and (AccountLinks.minecraftAccount eq minecraft.id)
        }.firstOrNull()

    suspend fun getOrCreateLinkRequest(discordUserId: ULong, username: String) = suspendTransaction(db) {
        val discord = getDiscordAccountOrNull(discordUserId)?.apply {
            lastKnownUsername = username
        } ?: DiscordAccountEntity.new {
            this.userId = discordUserId
            this.lastKnownUsername = username
        }

        val request = discord.linkRequest ?: LinkRequestEntity.new {
            discordAccount = discord
            code = generateUniqueCode()
        }

        LinkRequestResult(request.code)
    }

    fun consumeCodeAndLink(code: String, uuid: Uuid, name: String) = transaction(db) {
        val request = LinkRequestEntity.find {
            LinkRequests.code eq code
        }.firstOrNull() ?: return@transaction null

        val discord = request.discordAccount

        val minecraft = getMinecraftAccountOrNull(uuid)?.apply {
            lastKnownName = name
        } ?: MinecraftAccountEntity.new {
            this.uuid = uuid
            lastKnownName = name
        }

        getAccountLinkOrNull(discord, minecraft) ?: AccountLinkEntity.new {
            discordAccount = discord
            minecraftAccount = minecraft
            linkedAt = Instant.now()
        }

        request.delete()

        DiscordAccountInfo(discord.userId, discord.lastKnownUsername)
    }

    suspend fun listLinkedMinecraftAccounts(userId: ULong): List<MinecraftAccountInfo> = suspendTransaction(db) {
        val discord = getDiscordAccountOrNull(userId) ?: return@suspendTransaction emptyList()
        discord.links.map { link ->
            val mc = link.minecraftAccount
            MinecraftAccountInfo(mc.uuid, mc.lastKnownName)
        }
    }

    suspend fun getLinkOrNull(discordUserId: ULong, minecraftUuid: Uuid) = suspendTransaction(db) {
        val discord = getDiscordAccountOrNull(discordUserId) ?: return@suspendTransaction null
        val minecraft = getMinecraftAccountOrNull(minecraftUuid) ?: return@suspendTransaction null
        Pair(
            DiscordAccountInfo(discord.userId, discord.lastKnownUsername),
            MinecraftAccountInfo(minecraft.uuid, minecraft.lastKnownName)
        )
    }

    suspend fun unlink(discordUserId: ULong, minecraftUuid: Uuid) = suspendTransaction(db) {
        val discord = getDiscordAccountOrNull(discordUserId) ?: return@suspendTransaction false
        val minecraft = getMinecraftAccountOrNull(minecraftUuid) ?: return@suspendTransaction false
        val link = getAccountLinkOrNull(discord, minecraft) ?: return@suspendTransaction false
        link.delete()
        true
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
