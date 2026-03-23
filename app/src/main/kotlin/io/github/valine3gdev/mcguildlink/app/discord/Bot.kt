package io.github.valine3gdev.mcguildlink.app.discord

import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.requestMembers
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.component.section
import dev.kord.rest.builder.component.separator
import dev.kord.rest.builder.message.container
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.config.BotConfig
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import io.github.valine3gdev.mcguildlink.app.util.getOrCreateLinkRequest
import io.github.valine3gdev.mcguildlink.app.util.listLinkedMinecraftAccounts
import io.github.valine3gdev.mcguildlink.app.util.unlink
import kotlinx.coroutines.flow.collect
import kotlin.uuid.Uuid


private val logger = KotlinLogging.logger {}


class Bot(
    private val config: BotConfig,
    private val accountLinkService: AccountLinkService,
) {
    companion object {
        private const val CREATE_PANEL_COMMAND_NAME = "create_panel"
        private const val START_LINK_BUTTON_ID = "start_link_button"
        private const val LIST_LINK_BUTTON_ID = "list_link_button"
        private const val UNLINK_BUTTON_ID_PREFIX = "unlink_button:"
        private const val UNLINK_MODAL_ID_PREFIX = "unlink_confirm_modal:"
        private const val UNLINK_CONFIRM_CHECKBOX_ID = "unlink_confirm_checkbox"
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
        kord.on<GuildModalSubmitInteractionCreateEvent> { handleUnlinkConfirm() }

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
                val request = accountLinkService.getOrCreateLinkRequest(interaction.user)
                logger.info { "Requesting link code for user ${interaction.user.tag} (${interaction.user.id}): ${request.code}" }
                interaction.respondEphemeral {
                    content =
                        """
                            コードは以下の通りです。
                            ```
                            ${request.code}
                            ```
                        """.trimIndent()
                }
            }

            LIST_LINK_BUTTON_ID -> {
                val minecraftAccounts = accountLinkService.listLinkedMinecraftAccounts(interaction.user)

                if (minecraftAccounts.isEmpty()) {
                    interaction.respondEphemeral {
                        content = "あなたの Discordアカウントに紐付けられた Minecraftアカウントはありません。"
                    }
                    return
                }

                // TODO: ページネーションしないと大量のアカウントを紐付けている場合に送信できなくなる可能性がある
                interaction.respondEphemeral {
                    flags = MessageFlags(MessageFlag.IsComponentsV2)

                    container {
                        textDisplay(
                            """
                                ## 紐付けられたアカウント
                                以下の Minecraftアカウントがあなたの Discordアカウントに紐付けられています。
                                紐付けを解除したいアカウントがある場合は、各アカウントの「解除」ボタンを押してください。
                            """.trimIndent()
                        )

                        separator {
                            spacing = SeparatorSpacingSize.Large
                            divider = false
                        }

                        minecraftAccounts.forEach { minecraft ->
                            separator(SeparatorSpacingSize.Large)

                            val uuid = minecraft.uuid.toHexDashString()

                            section {
                                textDisplay(
                                    """
                                        - 名前: **${minecraft.lastKnownName}**
                                        - UUID: `$uuid`
                                    """.trimIndent()
                                )
                                thumbnailAccessory {
                                    url = "https://mc-heads.net/avatar/$uuid"
                                }
                            }
                            actionRow {
                                interactionButton(
                                    ButtonStyle.Danger,
                                    "$UNLINK_BUTTON_ID_PREFIX$uuid/${interaction.user.id}",
                                ) {
                                    emoji(ReactionEmoji.Unicode("\uD83D\uDCCB"))
                                    label = "解除"
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                if (!interaction.componentId.startsWith(UNLINK_BUTTON_ID_PREFIX)) {
                    logger.warn { "Unknown button interaction: ${interaction.componentId}" }
                    interaction.respondEphemeral {
                        content = "未知の操作です。"
                    }
                }

                val data = interaction.componentId.removePrefix(UNLINK_BUTTON_ID_PREFIX)
                val (uuidStr, userId) = data.split("/")
                val uuid = Uuid.parseHexDash(uuidStr)

                val (_, minecraft) = accountLinkService.getLinkOrNull(userId.toULong(), uuid) ?: run {
                    interaction.respondEphemeral {
                        content = "アカウント情報を取得できませんでした。すでに解除されている可能性があります。"
                    }
                    return
                }

                interaction.modal(
                    title = "アカウントの紐付け解除",
                    customId = "$UNLINK_MODAL_ID_PREFIX$data"
                ) {
                    textDisplay {
                        content =
                            """
                                本当に **${minecraft.lastKnownName}** との紐付けを解除しますか？
                                この操作は取り消せません。
                            """.trimIndent()
                    }

                    label("内容を確認し、解除に同意します。") {
                        checkbox(UNLINK_CONFIRM_CHECKBOX_ID) {
                            default = false
                        }
                    }
                }
            }
        }
    }

    private suspend fun GuildModalSubmitInteractionCreateEvent.handleUnlinkConfirm() {
        if (!interaction.modalId.startsWith(UNLINK_MODAL_ID_PREFIX)) {
            return
        }
        val checkbox = interaction.checkboxes[UNLINK_CONFIRM_CHECKBOX_ID] ?: return
        if (!checkbox.value) {
            interaction.respondEphemeral {
                content = """
                        紐付けの解除をキャンセルしました。
                        解除する場合は、内容を確認し、チェックボックスに同意してください。
                    """.trimIndent()
            }
            return
        }

        val data = interaction.modalId.removePrefix(UNLINK_MODAL_ID_PREFIX)
        val (uuidStr, userId) = data.split("/")
        val uuid = Uuid.parseHexDash(uuidStr)

        if (userId != interaction.user.id.toString()) {
            interaction.respondEphemeral {
                content = "不正な操作です。このモーダルはあなたのものではありません。"
            }
            return
        }

        accountLinkService.unlink(interaction.user, uuid)
        interaction.respondEphemeral {
            content = """
                    アカウントの紐付けを解除しました。
                """.trimIndent()
        }
    }
}
