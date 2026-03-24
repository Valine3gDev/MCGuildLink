package io.github.valine3gdev.mcguildlink.app.discord.registry

import dev.kord.common.annotation.KordDsl
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent


data class ChatInputCommand(
    val name: String,
    val description: String,
    val requiredBotPermissions: Permissions? = null,
    val requiredUserRoleIds: Set<Snowflake> = emptySet(),
    private val handler: Handler
) {
    typealias Handler = suspend GuildChatInputCommandInteractionCreateEvent.() -> Unit

    private suspend fun GuildChatInputCommandInteractionCreateEvent.preDispatch(): Boolean {
        requiredBotPermissions?.let { permissions ->
            if (!interaction.appPermissions.contains(permissions)) {
                val required = permissions - interaction.appPermissions
                interaction.respondEphemeral {
                    content = """
                        このコマンドを実行するための権限が Bot にありません。
                        必要な権限: ${required.values.joinToString(", ") { "`$it`" }}
                    """.trimIndent()
                }
                return false
            }
        }
        return true
    }

    suspend fun dispatch(event: GuildChatInputCommandInteractionCreateEvent) {
        with(event) {
            preDispatch() || return
        }

        handler.invoke(event)
    }

    suspend fun createCommand(kord: Kord, guildId: Snowflake) {
        kord.createGuildChatInputCommand(guildId, name, description) {}
    }
}

@KordDsl
class ChatInputCommandBuilder(var name: String, var description: String) {
    var requiredBotPermissions: Permissions? = null
    val requiredUserRoleIds: MutableSet<Snowflake> = mutableSetOf()

    private lateinit var handler: ChatInputCommand.Handler

    fun handle(action: ChatInputCommand.Handler) {
        handler = action
    }

    fun build() = ChatInputCommand(
        name = name,
        description = description,
        requiredBotPermissions = requiredBotPermissions,
        requiredUserRoleIds = requiredUserRoleIds,
        handler = handler
    )
}
