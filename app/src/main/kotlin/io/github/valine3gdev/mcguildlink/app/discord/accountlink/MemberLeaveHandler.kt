package io.github.valine3gdev.mcguildlink.app.discord.accountlink

import dev.kord.core.Kord
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.on
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.discord.logging.AuditLogSender
import io.github.valine3gdev.mcguildlink.app.discord.logging.sendMemberBannedBlocked
import io.github.valine3gdev.mcguildlink.app.discord.logging.sendMemberLeaveUnlinked
import io.github.valine3gdev.mcguildlink.app.service.AccountBlockService
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockResult
import io.github.valine3gdev.mcguildlink.app.util.blockDiscordAccount
import io.github.valine3gdev.mcguildlink.app.util.unlinkByDiscord


private val logger = KotlinLogging.logger {}

context(accountLinkService: AccountLinkService, accountBlockService: AccountBlockService, auditLogSender: AuditLogSender)
internal fun Kord.installAccountLinkMemberLeaveHandler() {
    on<MemberLeaveEvent> {
        val linkedMinecraftAccounts = accountLinkService.unlinkByDiscord(user)
        if (linkedMinecraftAccounts.isEmpty()) {
            return@on
        }

        auditLogSender.sendMemberLeaveUnlinked(user, linkedMinecraftAccounts)
    }

    on<BanAddEvent> {
        logger.info { "User ${user.username} was banned from guild ${this.guildId}." }
        when (val result = accountBlockService.blockDiscordAccount(user)) {
            is BlockResult.Success -> auditLogSender.sendMemberBannedBlocked(result)

            BlockResult.AlreadyBlocked -> Unit
        }
    }
}
