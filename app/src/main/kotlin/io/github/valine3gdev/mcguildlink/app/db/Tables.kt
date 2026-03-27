package io.github.valine3gdev.mcguildlink.app.db

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.timestamp


object DiscordAccounts : IntIdTable("discord_accounts") {
    val userId = ulong("user_id").uniqueIndex()
    val lastKnownUsername = varchar("last_known_username", length = 32)
}


object MinecraftAccounts : IntIdTable("minecraft_accounts") {
    val uuid = uuid("uuid").uniqueIndex()
    val lastKnownName = varchar("last_known_name", length = 16)
}


object AccountLinks : IntIdTable("account_links") {
    val discordAccount = reference("discord_account_id", DiscordAccounts)
    val minecraftAccount = reference("minecraft_account_id", MinecraftAccounts)
    val linkedAt = timestamp("linked_at")

    init {
        uniqueIndex(discordAccount, minecraftAccount)
    }
}


object LinkRequests : IntIdTable("link_requests") {
    val discordAccount = reference("discord_account_id", DiscordAccounts).uniqueIndex()
    val code = varchar("code", length = 64).uniqueIndex()
}


object BlockGroups : IntIdTable("block_groups") {
    val rootDiscordAccount = reference("root_discord_account_id", DiscordAccounts)
    val createdAt = timestamp("created_at")
}


object BlockedDiscordAccounts : IntIdTable("blocked_discord_accounts") {
    val blockGroup = reference("block_group_id", BlockGroups)
    val discordAccount = reference("discord_account_id", DiscordAccounts).uniqueIndex()
}


object BlockedMinecraftAccounts : IntIdTable("blocked_minecraft_accounts") {
    val blockGroup = reference("block_group_id", BlockGroups)
    val minecraftAccount = reference("minecraft_account_id", MinecraftAccounts).uniqueIndex()
}
