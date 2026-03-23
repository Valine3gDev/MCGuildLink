package io.github.valine3gdev.mcguildlink.app.discord

import dev.kord.core.Kord
import dev.kord.core.behavior.requestMembers
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.config.BotConfig
import kotlinx.coroutines.flow.collect


private val logger = KotlinLogging.logger {}


class Bot(
    private val config: BotConfig,
) {
    suspend fun start() {
        val kord = Kord(config.token) {
        }

        kord.createGuildChatInputCommand(
            config.guild,
            "create_panel",
            "認証を開始するためのパネルを送信します。"
        ) { disableCommandInGuilds() }

        kord.on<GuildChatInputCommandInteractionCreateEvent> {}

        kord.on<GuildButtonInteractionCreateEvent> {}

        kord.on<GuildCreateEvent> {
            @OptIn(PrivilegedIntent::class)
            guild.requestMembers().collect()
        }

        kord.on<ReadyEvent> {
            logger.info { "Bot ready: ${self.username} (${kord.selfId})" }
        }

        kord.login {
            @OptIn(PrivilegedIntent::class)
            intents += Intent.GuildMembers + Intent.GuildPresences + Intent.GuildIntegrations
        }
    }
}
