package io.github.valine3gdev.mcguildlink.app.discord.accountlink.interactions

import dev.kord.common.entity.SeparatorSpacingSize
import dev.kord.core.entity.interaction.ActionInteraction
import dev.kord.rest.builder.component.ContainerBuilder
import dev.kord.rest.builder.component.separator
import io.github.valine3gdev.mcguildlink.app.discord.registry.EphemeralPagination
import io.github.valine3gdev.mcguildlink.app.discord.registry.InteractionRegistry
import io.github.valine3gdev.mcguildlink.app.discord.registry.PaginationSnapshotPage
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockedAccountGroupInfo


private val blockedAccountsPagination = EphemeralPagination(
    prefix = "list_block_page_button:",
    pageSize = 10,
    renderPage = { _, page -> applyBlockedAccountsPage(page) },
)

/**
 * ブロック済みアカウント一覧のページ送りボタンを登録します。
 */
internal fun InteractionRegistry.installBlockAccountPagination() {
    blockedAccountsPagination.installPaginationButton()
}

/**
 * ブロックグループ一覧をエフェメラルページネーションで返信します。
 */
internal suspend fun respondBlockedAccountsPaginated(
    interaction: ActionInteraction,
    groups: List<BlockedAccountGroupInfo>,
) {
    blockedAccountsPagination.respondEphemeralPaginatedSnapshot(interaction, groups)
}

/**
 * ブロックグループ一覧 1 ページ分の本文を描画します。
 */
private fun ContainerBuilder.applyBlockedAccountsPage(
    page: PaginationSnapshotPage<BlockedAccountGroupInfo>,
) {
    textDisplay(
        """
            ## ブロックされているアカウント
            ブロックは root の Discordアカウント単位で表示しています。
            `/block remove` で対象ユーザーを指定すると、そのユーザーが属する block group 全体が解除されます。
        """.trimIndent()
    )

    separator {
        divider = false
    }

    page.entries.forEachIndexed { index, group ->
        if (index > 0) {
            separator(SeparatorSpacingSize.Large)
        }

        textDisplay(
            """
                - root: **${group.rootDiscordAccount.lastKnownUsername}** (`${group.rootDiscordAccount.userId}`)
                - ブロック中の Discordアカウント数: ${group.blockedDiscordAccounts}
                - ブロック中の Minecraftアカウント数: ${group.blockedMinecraftAccounts}
                - 作成日時: <t:${group.createdAt.epochSecond}:f>
            """.trimIndent()
        )
    }
}
