package io.github.valine3gdev.mcguildlink.app.discord

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.Permission
import dev.kord.common.entity.SeparatorSpacingSize
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.requestMembers
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.message.container
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.config.BotConfig
import kotlinx.coroutines.flow.collect


private val logger = KotlinLogging.logger {}


class Bot(
    private val config: BotConfig,
) {
    companion object {
        private const val CREATE_PANEL_COMMAND_NAME = "create_panel"
        private const val START_LINK_BUTTON_ID = "start_link_button"
        private const val LIST_LINK_BUTTON_ID = "list_link_button"
    }

    suspend fun start() {
        val kord = Kord(config.token) {
        }

        kord.createGuildChatInputCommand(
            config.guild,
            CREATE_PANEL_COMMAND_NAME,
            "紐付けを開始するためのパネルを送信します。"
        ) { disableCommandInGuilds() }

        kord.on<GuildChatInputCommandInteractionCreateEvent> { handleCreatePanelCommand() }
        kord.on<GuildButtonInteractionCreateEvent> { handleButtonInteraction() }

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

    private suspend fun GuildChatInputCommandInteractionCreateEvent.handleCreatePanelCommand() {
        if (interaction.command.rootName != CREATE_PANEL_COMMAND_NAME) {
            return
        }

        if (!interaction.appPermissions.contains(Permission.ViewChannel + Permission.SendMessages)) {
            interaction.respondEphemeral {
                content =
                    """
                    このチャンネルにメッセージを送信する権限がありません。
                    `チャンネルを見る` と `メッセージの送信と投稿の作成` の両方の権限が必要です。
                    """.trimIndent()
            }
            return
        }

        val deferred = interaction.deferEphemeralResponse()

        interaction.channel.createMessage {
            flags = MessageFlags(MessageFlag.IsComponentsV2)

            container {
                textDisplay(
                    """
                    Minecraftアカウントと Discordアカウントを紐付けます。
                    「MCアカウントと紐付ける」ボタンを押して、手順に従ってください。
                    「紐付けられたアカウントを確認する」ボタンを押すと、現在紐付けられているアカウントの一覧を確認できます。
                    
                    ## 紐付け手順
                    TODO
                    """.trimIndent()
                )

                separator(SeparatorSpacingSize.Large)

                actionRow {
                    interactionButton(
                        ButtonStyle.Primary,
                        START_LINK_BUTTON_ID,
                    ) {
                        emoji(ReactionEmoji.Unicode("\uD83D\uDD17"))
                        label = "MCアカウントと紐付ける"
                    }

                    interactionButton(
                        ButtonStyle.Secondary,
                        LIST_LINK_BUTTON_ID,
                    ) {
                        emoji(ReactionEmoji.Unicode("\uD83D\uDCCB"))
                        label = "紐付けられたアカウントを確認する"
                    }
                }
            }
        }

        deferred.respond {
            content = "パネルを作成しました！"
        }
    }

    private suspend fun GuildButtonInteractionCreateEvent.handleButtonInteraction() {
        when (interaction.componentId) {
            START_LINK_BUTTON_ID -> {
                interaction.respondEphemeral {
                    content = "MCアカウントと紐付ける手順はまだ実装されていません。"
                }
            }

            LIST_LINK_BUTTON_ID -> {
                interaction.respondEphemeral {
                    content = "紐付けられたアカウントの一覧を表示する機能はまだ実装されていません。"
                }
            }
        }
    }
}
