package io.github.valine3gdev.mcguildlink.app.discord.registry

import dev.kord.common.annotation.KordDsl
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.on


@KordDsl
class InteractionRegistry(kord: Kord) {
    private val buttonInteractions = mutableSetOf<InteractionButton<*>>()
    private val modalInteractions = mutableSetOf<InteractionModal<*>>()

    init {
        kord.on<GuildButtonInteractionCreateEvent> { handleButtonInteractions() }
        kord.on<GuildModalSubmitInteractionCreateEvent> { handleModalSubmissions() }
    }

    private suspend fun GuildButtonInteractionCreateEvent.handleButtonInteractions() {
        val button = buttonInteractions.find { it.id.matches(interaction.componentId) } ?: return
        button.dispatch(this, interaction.componentId)
    }

    fun <T, C> interactionButton(
        id: C,
        handler: InteractionButton.Handler<T>,
    ) where C : CustomId, C : CustomIdParser<T> {
        buttonInteractions += InteractionButton.parsed(id, handler)
    }

    fun interactionButton(
        id: String,
        handler: InteractionButton.Handler<Nothing>,
    ) {
        buttonInteractions += InteractionButton(EqualsCustomId(id), handler)
    }

    private suspend fun GuildModalSubmitInteractionCreateEvent.handleModalSubmissions() {
        val modal = modalInteractions.find { it.id.matches(interaction.modalId) } ?: return
        modal.dispatch(this, interaction.modalId)
    }

    fun <T, C> interactionModal(
        id: C,
        handler: InteractionModal.Handler<T>,
    ) where C : CustomId, C : CustomIdParser<T> {
        modalInteractions += InteractionModal.parsed(id, handler)
    }

    fun interactionModal(
        id: String,
        handler: InteractionModal.Handler<Nothing>,
    ) {
        modalInteractions += InteractionModal(EqualsCustomId(id), handler)
    }
}

data class InteractionButton<T>(
    val id: CustomId,
    private val handler: Handler<T>,
    private val parser: ((String) -> T?)? = null
) {
    typealias Handler<T> = suspend GuildButtonInteractionCreateEvent.(T?) -> Unit

    suspend fun dispatch(event: GuildButtonInteractionCreateEvent, componentId: String) {
        val data = parser?.invoke(componentId)
        handler(event, data)
    }

    companion object {
        fun <T, C> parsed(
            id: C,
            handler: Handler<T>
        ): InteractionButton<T>
                where C : CustomId, C : CustomIdParser<T> {
            return InteractionButton(id = id, handler = handler, parser = id::parse)
        }
    }
}

data class InteractionModal<T>(
    val id: CustomId,
    private val handler: Handler<T>,
    private val parser: ((String) -> T?)? = null
) {
    typealias Handler<T> = suspend GuildModalSubmitInteractionCreateEvent.(T?) -> Unit

    suspend fun dispatch(event: GuildModalSubmitInteractionCreateEvent, modalId: String) {
        val data = parser?.invoke(modalId)
        handler(event, data)
    }

    companion object {
        fun <T, C> parsed(
            id: C,
            handler: Handler<T>
        ): InteractionModal<T>
                where C : CustomId, C : CustomIdParser<T> {
            return InteractionModal(id = id, handler = handler, parser = id::parse)
        }
    }
}
