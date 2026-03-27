package io.github.valine3gdev.mcguildlink.app.discord

import dev.kord.core.Kord
import dev.kord.core.behavior.requestMembers
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.config.BotConfig
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.installAccountLinkHandlers
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.installCommands
import io.github.valine3gdev.mcguildlink.app.discord.registry.InteractionRegistry
import io.github.valine3gdev.mcguildlink.app.service.AccountBlockService
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import kotlinx.coroutines.flow.collect


private val logger = KotlinLogging.logger {}

class Bot(
    private val config: BotConfig,
    private val accountLinkService: AccountLinkService,
    private val accountBlockService: AccountBlockService,
) {
    suspend fun start() {
        val kord = Kord(config.token) {
        }

        val interactions = InteractionRegistry(kord)
        context(accountLinkService, accountBlockService) {
            installAccountLinkHandlers(kord, interactions)

            installCommands(kord, config.guild)
        }

        // TODO: 紐付け Ban 状態を管理するコマンド (add, remove, list)
        // TODO: 紐付け一覧を表示する特殊権限用コマンド (discord, minecraft, all)

        kord.on<GuildCreateEvent> {
            @OptIn(PrivilegedIntent::class)
            guild.requestMembers().collect()
        }

        kord.on<ReadyEvent> {
            logger.info { "Bot ready: ${self.username} (${kord.selfId})" }
        }

        kord.login {
            intents {
                @OptIn(PrivilegedIntent::class)
                +Intent.GuildMembers

                +Intent.Guilds
                +Intent.GuildModeration
            }
        }
    }
}
