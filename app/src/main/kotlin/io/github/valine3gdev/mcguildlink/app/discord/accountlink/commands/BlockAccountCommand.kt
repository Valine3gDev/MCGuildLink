package io.github.valine3gdev.mcguildlink.app.discord.accountlink.commands

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user
import io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions.respondBlockedAccountsPaginated
import io.github.valine3gdev.mcguildlink.app.discord.util.guardUserRole
import io.github.valine3gdev.mcguildlink.app.discord.util.handleSub
import io.github.valine3gdev.mcguildlink.app.service.AccountBlockService
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockResult
import io.github.valine3gdev.mcguildlink.app.service.dto.UnblockResult
import io.github.valine3gdev.mcguildlink.app.util.blockDiscordAccount
import io.github.valine3gdev.mcguildlink.app.util.unblockDiscordAccount


private const val USER_OPTION_KEY = "user"

context(accountBlockService: AccountBlockService)
internal suspend fun Kord.installBlockAccountCommand(guildId: Snowflake, moderatorRole: Snowflake) {
    createGuildChatInputCommand(
        guildId,
        "block",
        "Discordアカウントをブロックして、既存の紐付けと新規の紐付けを禁止します。"
    ) {
        disableCommandInGuilds()

        subCommand("add", "Discordアカウントをブロックします。") {
            user(USER_OPTION_KEY, "ブロックする Discordユーザー") {
                required = true
            }
        }

        subCommand("remove", "Discordアカウントのブロックを解除します。") {
            user(USER_OPTION_KEY, "ブロック解除する Discordユーザー") {
                required = true
            }
        }

        subCommand("list", "ブロックされている Discordアカウントの一覧を表示します。")

    }.handleSub("add") {
        guardUserRole(moderatorRole) || return@handleSub

        val target = interaction.command.users[USER_OPTION_KEY] ?: run {
            interaction.respondEphemeral {
                content = "ブロック対象の Discordユーザーを指定してください。"
            }
            return@handleSub
        }

        when (val result = accountBlockService.blockDiscordAccount(target)) {
            is BlockResult.Success -> interaction.respondEphemeral {
                content = buildString {
                    appendLine("Discordアカウントをブロックしました。")

                    if (result.blockedDiscordAccountInfos.isNotEmpty()) {
                        appendLine()
                        appendLine(
                            """
                            ブロックした Discordアカウント:
                            ${result.blockedDiscordAccountInfos.joinToString("\n") {
                                "- **${it.lastKnownUsername}** (`${it.userId}`)"
                            }}
                            """.trimIndent()
                        )
                    }

                    if (result.blockedMinecraftAccountInfos.isNotEmpty()) {
                        appendLine()
                        appendLine(
                            """
                            ブロックした Minecraftアカウント:
                            ${result.blockedMinecraftAccountInfos.joinToString("\n") {
                                "- **${it.lastKnownName}** (`${it.uuid}`)"
                            }}
                            """.trimIndent()
                        )
                    }
                }.trimEnd()
            }

            BlockResult.AlreadyBlocked -> interaction.respondEphemeral {
                content = "その Discordアカウントは既にブロックされています。"
            }
        }
    }.handleSub("remove") {
        guardUserRole(moderatorRole) || return@handleSub

        val target = interaction.command.users[USER_OPTION_KEY] ?: run {
            interaction.respondEphemeral {
                content = "ブロック解除対象の Discordユーザーを指定してください。"
            }
            return@handleSub
        }

        when (val result = accountBlockService.unblockDiscordAccount(target)) {
            is UnblockResult.Success -> interaction.respondEphemeral {
                content = """
                    Discordアカウントのブロックを解除しました。
                    - 対象 block group: **${result.blockGroup.rootDiscordAccount.lastKnownUsername}** (`${result.blockGroup.rootDiscordAccount.userId}`)
                    - 解除した Discordアカウント数: ${result.blockGroup.blockedDiscordAccounts}
                    - 解除した Minecraftアカウント数: ${result.blockGroup.blockedMinecraftAccounts}
                """.trimIndent()
            }

            UnblockResult.NotBlocked -> interaction.respondEphemeral {
                content = "その Discordアカウントはブロックされていません。"
            }
        }
    }.handleSub("list") {
        guardUserRole(moderatorRole) || return@handleSub

        val blockedGroups = accountBlockService.listBlockedDiscordAccountGroups()
        if (blockedGroups.isEmpty()) {
            interaction.respondEphemeral {
                content = "ブロックされている Discordアカウントはありません。"
            }
            return@handleSub
        }

        respondBlockedAccountsPaginated(interaction, blockedGroups)
    }
}
