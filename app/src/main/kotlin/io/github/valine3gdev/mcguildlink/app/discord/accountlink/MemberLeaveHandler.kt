package io.github.valine3gdev.mcguildlink.app.discord.accountlink

import dev.kord.core.Kord
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.on
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.service.AccountBlockService
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import io.github.valine3gdev.mcguildlink.app.util.blockDiscordAccount
import io.github.valine3gdev.mcguildlink.app.util.unlinkByDiscord


private val logger = KotlinLogging.logger {}

context(accountLinkService: AccountLinkService, accountBlockService: AccountBlockService)
internal fun Kord.installAccountLinkMemberLeaveHandler() {
    on<MemberLeaveEvent> {
        accountLinkService.unlinkByDiscord(user)
    }

    on<BanAddEvent> {
        logger.info { "User ${user.username} was banned from guild ${this.guildId}." }
        accountBlockService.blockDiscordAccount(user)
    }
}
