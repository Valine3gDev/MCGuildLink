package io.github.valine3gdev.mcguildlink.app.discord.accountlink

import dev.kord.core.Kord
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.commands.installCreatePanelCommand
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.installAccountLinkButtons
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.installUnlinkHandlers
import io.github.valine3gdev.mcguildlink.app.discord.registry.InteractionRegistry
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService


context(accountLinkService: AccountLinkService)
fun installAccountLinkHandlers(
    kord: Kord,
    interactions: InteractionRegistry,
) {
    with(interactions) {
        installCreatePanelCommand()
        installAccountLinkButtons()
        installUnlinkHandlers()
    }

    with(kord) {
        installAccountLinkMemberLeaveHandler()
    }
}
