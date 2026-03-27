package io.github.valine3gdev.mcguildlink.app.discord.util

import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent


suspend fun GuildChatInputCommandInteractionCreateEvent.guardBotPermissions(requiredBotPermissions: Permissions): Boolean {
    if (!interaction.appPermissions.contains(requiredBotPermissions)) {
        val required = requiredBotPermissions - interaction.appPermissions
        interaction.respondEphemeral {
            content = """
                        このコマンドを実行するための権限が Bot にありません。
                        必要な権限: ${required.values.joinToString(", ") { "`$it`" }}
                    """.trimIndent()
        }
        return false
    }
    return true
}

suspend fun GuildChatInputCommandInteractionCreateEvent.guardUserRole(requiredUserRoleId: Snowflake): Boolean {
    if (!interaction.user.roleIds.contains(requiredUserRoleId)) {
        interaction.respondEphemeral {
            content = "このコマンドを実行する権限があなたにありません。"
        }
        return false
    }
    return true
}
