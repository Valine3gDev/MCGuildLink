package io.github.valine3gdev.mcguildlink.app.db

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass


class DiscordAccountEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DiscordAccountEntity>(DiscordAccounts)

    var discordUserId by DiscordAccounts.discordUserId
    var lastKnownUsername by DiscordAccounts.lastKnownUsername
    var createdAt by DiscordAccounts.createdAt
    var updatedAt by DiscordAccounts.updatedAt

    val links by AccountLinkEntity referrersOn AccountLinks.discordAccount
    val linkRequest by LinkRequestEntity optionalBackReferencedOn LinkRequests.discordAccount
}


class MinecraftAccountEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MinecraftAccountEntity>(MinecraftAccounts)

    var uuid by MinecraftAccounts.uuid
    var lastKnownName by MinecraftAccounts.lastKnownName
    var createdAt by MinecraftAccounts.createdAt
    var updatedAt by MinecraftAccounts.updatedAt

    val links by AccountLinkEntity referrersOn AccountLinks.minecraftAccount
}


class AccountLinkEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AccountLinkEntity>(AccountLinks)

    var discordAccount by DiscordAccountEntity referencedOn AccountLinks.discordAccount
    var minecraftAccount by MinecraftAccountEntity referencedOn AccountLinks.minecraftAccount
    var linkedAt by AccountLinks.linkedAt
}


class LinkRequestEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LinkRequestEntity>(LinkRequests)

    var discordAccount by DiscordAccountEntity referencedOn LinkRequests.discordAccount
    var code by LinkRequests.code
    var createdAt by LinkRequests.createdAt
    var updatedAt by LinkRequests.updatedAt
}
