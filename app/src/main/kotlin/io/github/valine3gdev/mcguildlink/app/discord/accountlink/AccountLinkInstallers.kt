package io.github.valine3gdev.mcguildlink.app.discord.accountlink

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.commands.installBlockAccountCommand
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.commands.installCreatePanelCommand
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.commands.installListLinksCommand
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.installAccountLinkButtons
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.installAccountLinksPagination
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.installBlockAccountPagination
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.installUnlinkHandlers
import io.github.valine3gdev.mcguildlink.app.discord.registry.InteractionRegistry
import io.github.valine3gdev.mcguildlink.app.service.AccountBlockService
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService


context(accountLinkService: AccountLinkService, accountBlockService: AccountBlockService)
fun installAccountLinkHandlers(
    kord: Kord,
    interactions: InteractionRegistry,
) {
    with(interactions) {
        installAccountLinkButtons()
        installAccountLinksPagination()
        installBlockAccountPagination()
        installUnlinkHandlers()
    }

    with(kord) {
        installAccountLinkMemberLeaveHandler()
    }
}

context(accountLinkService: AccountLinkService, accountBlockService: AccountBlockService)
suspend fun installCommands(kord: Kord, guildId: Snowflake) {
    with(kord) {
        installBlockAccountCommand(guildId)
        installCreatePanelCommand(guildId)
        installListLinksCommand(guildId)
    }
}
