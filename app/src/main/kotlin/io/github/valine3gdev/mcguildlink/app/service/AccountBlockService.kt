package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.AccountLinkEntity
import io.github.valine3gdev.mcguildlink.app.db.AccountLinks
import io.github.valine3gdev.mcguildlink.app.db.BlockGroupEntity
import io.github.valine3gdev.mcguildlink.app.db.BlockedDiscordAccountEntity
import io.github.valine3gdev.mcguildlink.app.db.BlockedMinecraftAccountEntity
import io.github.valine3gdev.mcguildlink.app.db.DiscordAccountEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequestEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequests
import io.github.valine3gdev.mcguildlink.app.db.MinecraftAccountEntity
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockedAccountGroupInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockResult
import io.github.valine3gdev.mcguildlink.app.service.dto.DiscordAccountInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.UnblockResult
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant


class AccountBlockService(
    private val db: Database,
    private val whitelistRefreshRequester: WhitelistRefreshRequester = NoopWhitelistRefreshRequester,
) {
    private fun BlockGroupEntity.toInfo() = BlockedAccountGroupInfo(
        rootDiscordAccount = DiscordAccountInfo(
            userId = rootDiscordAccount.userId,
            lastKnownUsername = rootDiscordAccount.lastKnownUsername,
        ),
        blockedDiscordAccounts = blockedDiscordAccounts.count().toInt(),
        blockedMinecraftAccounts = blockedMinecraftAccounts.count().toInt(),
        createdAt = createdAt,
    )

    private fun collectConnectedAccounts(
        rootDiscord: DiscordAccountEntity
    ): Pair<Set<DiscordAccountEntity>, Set<MinecraftAccountEntity>> {
        val discordAccounts = linkedSetOf(rootDiscord)
        val minecraftAccounts = linkedSetOf<MinecraftAccountEntity>()

        while (true) {
            var changed = false

            val discordIds = discordAccounts.mapTo(mutableListOf()) { it.id }
            val minecraftIds = minecraftAccounts.mapTo(mutableListOf()) { it.id }

            if (discordIds.isNotEmpty()) {
                AccountLinkEntity.find {
                    AccountLinks.discordAccount inList discordIds
                }.forEach { link ->
                    changed = minecraftAccounts.add(link.minecraftAccount) || changed
                }
            }

            if (minecraftIds.isNotEmpty()) {
                AccountLinkEntity.find {
                    AccountLinks.minecraftAccount inList minecraftIds
                }.forEach { link ->
                    changed = discordAccounts.add(link.discordAccount) || changed
                }
            }

            if (!changed) {
                return discordAccounts to minecraftAccounts
            }
        }
    }

    suspend fun blockDiscordAccount(discordUserId: ULong, username: String): BlockResult {
        val result = suspendTransaction(db) {
            val rootDiscord = AccountStore.getOrCreateDiscordAccount(discordUserId, username)
            if (rootDiscord.isBlocked()) {
                return@suspendTransaction BlockResult.AlreadyBlocked
            }

            val (discordAccounts, minecraftAccounts) = collectConnectedAccounts(rootDiscord)
            if (discordAccounts.any { it.isBlocked() } || minecraftAccounts.any { it.isBlocked() }) {
                return@suspendTransaction BlockResult.AlreadyBlocked
            }

            val blockGroup = BlockGroupEntity.new {
                rootDiscordAccount = rootDiscord
                createdAt = Instant.now()
            }

            discordAccounts.forEach { discord ->
                BlockedDiscordAccountEntity.new {
                    this.blockGroup = blockGroup
                    discordAccount = discord
                }
            }

            minecraftAccounts.forEach { minecraft ->
                BlockedMinecraftAccountEntity.new {
                    this.blockGroup = blockGroup
                    minecraftAccount = minecraft
                }
            }

            val discordIds = discordAccounts.mapTo(mutableListOf()) { it.id }
            val minecraftIds = minecraftAccounts.mapTo(mutableListOf()) { it.id }

            if (discordIds.isNotEmpty() || minecraftIds.isNotEmpty()) {
                AccountLinkEntity.find {
                    when {
                        discordIds.isNotEmpty() && minecraftIds.isNotEmpty() ->
                            (AccountLinks.discordAccount inList discordIds) or (AccountLinks.minecraftAccount inList minecraftIds)

                        discordIds.isNotEmpty() ->
                            AccountLinks.discordAccount inList discordIds

                        else ->
                            AccountLinks.minecraftAccount inList minecraftIds
                    }
                }.toList().forEach { it.delete() }
            }

            if (discordIds.isNotEmpty()) {
                LinkRequestEntity.find {
                    LinkRequests.discordAccount inList discordIds
                }.toList().forEach { it.delete() }
            }

            BlockResult.Success(
                rootDiscordAccount = DiscordAccountInfo(rootDiscord.userId, rootDiscord.lastKnownUsername),
                blockedDiscordAccounts = discordAccounts.size,
                blockedMinecraftAccounts = minecraftAccounts.size,
            )
        }

        if (result is BlockResult.Success) {
            whitelistRefreshRequester.requestRefresh()
        }

        return result
    }

    suspend fun unblockDiscordAccount(discordUserId: ULong): UnblockResult {
        val result = suspendTransaction(db) {
            val discord = AccountStore.getDiscordAccountOrNull(discordUserId) ?: return@suspendTransaction UnblockResult.NotBlocked
            val blockGroup = discord.blockedMembership?.blockGroup ?: return@suspendTransaction UnblockResult.NotBlocked
            val blockGroupInfo = blockGroup.toInfo()

            blockGroup.blockedDiscordAccounts.toList().forEach { it.delete() }
            blockGroup.blockedMinecraftAccounts.toList().forEach { it.delete() }
            blockGroup.delete()

            UnblockResult.Success(blockGroupInfo)
        }

        return result
    }

    suspend fun listBlockedDiscordAccountGroups() = suspendTransaction(db) {
        BlockGroupEntity.all()
            .sortedByDescending { it.createdAt }
            .map { it.toInfo() }
    }
}
