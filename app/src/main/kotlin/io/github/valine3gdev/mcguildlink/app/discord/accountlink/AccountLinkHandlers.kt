package io.github.valine3gdev.mcguildlink.app.discord.accountlink

import dev.kord.core.Kord
import io.github.valine3gdev.mcguildlink.app.discord.registry.InteractionRegistry
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService


fun installAccountLinkHandlers(
    kord: Kord,
    interactions: InteractionRegistry,
    accountLinkService: AccountLinkService,
) {
    with(interactions) {
        installCreatePanelCommand()
        installAccountLinkButtons(accountLinkService)
        installUnlinkHandlers(accountLinkService)
    }

    with(kord) {
        installAccountLinkMemberLeaveHandler(accountLinkService)
    }
}
