package io.github.valine3gdev.mcguildlink.app.discord.registry

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.MessageFlags
import dev.kord.common.entity.SeparatorSpacingSize
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.updateEphemeralMessage
import dev.kord.core.entity.User
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.rest.builder.component.ContainerBuilder
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.container
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


data class PaginationCustomIdData(
    val userId: Snowflake,
    val snapshotId: String,
    val page: Int,
)


data class PaginationSnapshotPage<T>(
    val snapshotId: String,
    val ownerUserId: Snowflake,
    val entries: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val totalEntries: Int,
    val expiresAt: Instant,
) {
    val hasPreviousPage: Boolean
        get() = currentPage > 0

    val hasNextPage: Boolean
        get() = currentPage + 1 < totalPages
}


private data class PaginationSnapshot<T>(
    val id: String,
    val ownerUserId: Snowflake,
    val entries: List<T>,
    val pageSize: Int,
    val expiresAt: Instant,
) {
    fun page(requestedPage: Int): PaginationSnapshotPage<T> {
        val totalEntries = entries.size
        val totalPages = if (totalEntries == 0) 1 else (totalEntries + pageSize - 1) / pageSize
        val currentPage = requestedPage.coerceIn(0, totalPages - 1)
        val fromIndex = currentPage * pageSize
        val toIndex = minOf(fromIndex + pageSize, totalEntries)

        return PaginationSnapshotPage(
            snapshotId = id,
            ownerUserId = ownerUserId,
            entries = entries.subList(fromIndex, toIndex),
            currentPage = currentPage,
            totalPages = totalPages,
            totalEntries = totalEntries,
            expiresAt = expiresAt,
        )
    }
}


class PaginationSnapshotStore<T>(
    private val ttl: Duration = 10.minutes,
) {
    private val snapshots = ConcurrentHashMap<String, PaginationSnapshot<T>>()

    fun create(snapshotId: String, ownerUserId: Snowflake, entries: List<T>, pageSize: Int): PaginationSnapshotPage<T> {
        require(pageSize > 0) { "pageSize must be positive" }

        pruneExpired()

        val snapshot = PaginationSnapshot(
            id = snapshotId,
            ownerUserId = ownerUserId,
            entries = entries.toList(),
            pageSize = pageSize,
            expiresAt = Instant.now().plusMillis(ttl.inWholeMilliseconds),
        )

        snapshots[snapshot.id] = snapshot
        return snapshot.page(0)
    }

    fun getPage(snapshotId: String, page: Int): PaginationSnapshotPage<T>? {
        pruneExpired()

        val snapshot = snapshots[snapshotId] ?: return null
        if (snapshot.expiresAt.isExpired()) {
            snapshots.remove(snapshotId, snapshot)
            return null
        }

        return snapshot.page(page)
    }

    fun remove(snapshotId: String) {
        snapshots.remove(snapshotId)
    }

    private fun pruneExpired(now: Instant = Instant.now()) {
        snapshots.entries.removeIf { it.value.expiresAt.isExpired(now) }
    }
}


class EphemeralPagination<T>(
    val prefix: String,
    val pageSize: Int,
    private val renderPage: ContainerBuilder.(User, PaginationSnapshotPage<T>) -> Unit,
    private val snapshotStore: PaginationSnapshotStore<T> = PaginationSnapshotStore(),
) {
    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }

    private val paginationCustomId = createPaginationCustomId(prefix)

    fun createSnapshot(interaction: ActionInteraction, entries: List<T>): PaginationSnapshotPage<T> =
        snapshotStore.create(
            snapshotId = interaction.id.value.toString(),
            ownerUserId = interaction.user.id,
            entries = entries,
            pageSize = pageSize,
        )

    fun getPage(paginationData: PaginationCustomIdData): PaginationSnapshotPage<T>? =
        snapshotStore.getPage(paginationData.snapshotId, paginationData.page)

    private fun createCustomIdString(user: User, snapshotId: String, page: Int): String =
        "${prefix}${user.id.value}/$snapshotId/$page"

    private fun ContainerBuilder.paginationButtons(
        user: User,
        page: PaginationSnapshotPage<T>,
    ) {
        if (page.totalPages <= 1) {
            return
        }

        separator(SeparatorSpacingSize.Large)

        actionRow {
            interactionButton(
                ButtonStyle.Primary,
                createCustomIdString(
                    user = user,
                    snapshotId = page.snapshotId,
                    page = (page.currentPage - 1).coerceAtLeast(0),
                ),
            ) {
                label = "◀"
                disabled = !page.hasPreviousPage
            }

            interactionButton(ButtonStyle.Secondary, "${prefix}disabled") {
                label = "${page.currentPage + 1} / ${page.totalPages} (${page.totalEntries})"
                disabled = true
            }

            interactionButton(
                ButtonStyle.Primary,
                createCustomIdString(
                    user = user,
                    snapshotId = page.snapshotId,
                    page = (page.currentPage + 1).coerceAtMost(page.totalPages - 1),
                ),
            ) {
                label = "▶"
                disabled = !page.hasNextPage
            }
        }
    }

    private fun MessageBuilder.paginatedSnapshot(
        user: User,
        page: PaginationSnapshotPage<T>,
    ) {
        flags = MessageFlags(MessageFlag.IsComponentsV2)

        container {
            renderPage(user, page)
            paginationButtons(user, page)
        }
    }

    private suspend fun updateEphemeral(
        event: ComponentInteractionCreateEvent,
        paginationData: PaginationCustomIdData,
        onInvalidUser: suspend ComponentInteractionCreateEvent.() -> Unit,
        onSnapshotMissing: MessageBuilder.() -> Unit,
    ) {
        if (paginationData.userId != event.interaction.user.id) {
            onInvalidUser(event)
            return
        }

        val page = getPage(paginationData)
        if (page == null || page.ownerUserId != event.interaction.user.id) {
            event.interaction.updateEphemeralMessage {
                onSnapshotMissing()
            }
            return
        }

        event.interaction.updateEphemeralMessage {
            paginatedSnapshot(event.interaction.user, page)
        }
    }

    context(registry: InteractionRegistry)
    fun installPaginationButton(
        onInvalidUser: suspend ComponentInteractionCreateEvent.() -> Unit = {
            interaction.respondEphemeral {
                content = "不正な操作です。このボタンはあなたのものではありません。"
            }
        },
        onSnapshotMissing: MessageBuilder.() -> Unit = {
            expiredPaginatedSnapshotMessage()
        },
    ) {
        registry.interactionButton(paginationCustomId) {
            val paginationData =
                it ?: error("Invalid pagination custom ID data for button: ${interaction.componentId}")

            updateEphemeral(this, paginationData, onInvalidUser, onSnapshotMissing)
        }
    }

    suspend fun respondEphemeralPaginatedSnapshot(
        interaction: ActionInteraction,
        entries: List<T>,
    ) {
        val firstPage = createSnapshot(interaction, entries)

        interaction.respondEphemeral {
            paginatedSnapshot(interaction.user, firstPage)
        }
    }
}


private fun Instant.isExpired(now: Instant = Instant.now()): Boolean = !isAfter(now)


fun createPaginationCustomId(prefix: String) = PrefixCustomId(prefix) { data ->
    data.split("/").takeIf { it.size == 3 }?.let { (userIdStr, snapshotId, pageStr) ->
        val userId = userIdStr.toULongOrNull()?.let(::Snowflake) ?: return@let null
        val page = pageStr.toIntOrNull() ?: return@let null
        PaginationCustomIdData(userId, snapshotId, page)
    }
}

fun MessageBuilder.expiredPaginatedSnapshotMessage(
    text: String = "一覧の有効期限が切れました。もう一度開き直してください。",
) {
    flags = MessageFlags(MessageFlag.IsComponentsV2)

    container {
        textDisplay(text)
    }
}
