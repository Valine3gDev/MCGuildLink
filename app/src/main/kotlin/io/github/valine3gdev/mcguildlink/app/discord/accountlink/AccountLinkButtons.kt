package io.github.valine3gdev.mcguildlink.app.discord.accountlink

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.SeparatorSpacingSize
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.component.section
import dev.kord.rest.builder.component.separator
import dev.kord.rest.builder.message.container
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.discord.registry.InteractionRegistry
import io.github.valine3gdev.mcguildlink.app.discord.registry.createCustomIdString
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import io.github.valine3gdev.mcguildlink.app.util.getLinkedMinecraftAccounts
import io.github.valine3gdev.mcguildlink.app.util.getOrCreateLinkRequest


private val logger = KotlinLogging.logger {}

context(accountLinkService: AccountLinkService)
internal fun InteractionRegistry.installAccountLinkButtons() {
    interactionButton(START_LINK_BUTTON_ID) {
        val request = accountLinkService.getOrCreateLinkRequest(interaction.user)
        logger.info { "Requesting link code for user ${interaction.user.tag} (${interaction.user.id}): ${request.code}" }
        interaction.respondEphemeral {
            content = """
                コードは以下の通りです。
                ```
                ${request.code}
                ```
            """.trimIndent()
        }
    }

    interactionButton(LIST_LINK_BUTTON_ID) {
        val minecraftAccounts = accountLinkService.getLinkedMinecraftAccounts(interaction.user)

        if (minecraftAccounts.isEmpty()) {
            interaction.respondEphemeral {
                content = "あなたの Discordアカウントに紐付けられた Minecraftアカウントはありません。"
            }
            return@interactionButton
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
                    val uuid = minecraft.uuid

                    separator(SeparatorSpacingSize.Large)

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
                            createCustomIdString(UNLINK_BUTTON_ID_PREFIX, interaction.user, uuid),
                        ) {
                            emoji(ReactionEmoji.Unicode("\uD83D\uDCCB"))
                            label = "解除"
                        }
                    }
                }
            }
        }
    }
}
