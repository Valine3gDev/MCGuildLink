@file:OptIn(KordPreview::class)

package io.github.valine3gdev.mcguildlink.app.discord.util

import dev.kord.common.annotation.KordPreview
import dev.kord.core.Kord
import dev.kord.core.entity.application.GuildChatInputCommand
import dev.kord.core.entity.interaction.GroupCommand
import dev.kord.core.entity.interaction.InteractionCommand
import dev.kord.core.entity.interaction.RootCommand
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on


typealias CommandEventHandler = suspend GuildChatInputCommandInteractionCreateEvent.() -> Unit

context(command: GuildChatInputCommand)
inline fun <reified COMMAND : InteractionCommand> Kord.onCommand(
    crossinline predicate: suspend (command: COMMAND) -> Boolean,
    crossinline block: CommandEventHandler
) = on<GuildChatInputCommandInteractionCreateEvent> {
    if (command.id != interaction.command.rootId) {
        return@on
    }
    val command = interaction.command as? COMMAND ?: return@on
    if (predicate(command)) {
        block.invoke(this)
    }
}

context(kord: Kord)
inline fun GuildChatInputCommand.handleRoot(
    crossinline block: CommandEventHandler
): GuildChatInputCommand {
    kord.onCommand<RootCommand>({ true }, block)
    return this
}

context(kord: Kord)
inline fun GuildChatInputCommand.handleSub(
    subCommandName: String,
    crossinline block: CommandEventHandler
): GuildChatInputCommand {
    kord.onCommand<SubCommand>({ it.name == subCommandName }, block)
    return this
}

context(kord: Kord)
inline fun GuildChatInputCommand.handleGroupedSub(
    groupName: String,
    subCommandName: String,
    crossinline block: CommandEventHandler
): GuildChatInputCommand {
    kord.onCommand<GroupCommand>({ it.groupName == groupName &&  it.name == subCommandName }, block)
    return this
}
