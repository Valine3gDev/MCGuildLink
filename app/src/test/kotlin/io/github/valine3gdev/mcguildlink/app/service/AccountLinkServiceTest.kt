package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.AccountLinkEntity
import io.github.valine3gdev.mcguildlink.app.db.DiscordAccountEntity
import io.github.valine3gdev.mcguildlink.app.db.LinkRequestEntity
import io.github.valine3gdev.mcguildlink.app.db.MinecraftAccountEntity
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockResult
import io.github.valine3gdev.mcguildlink.app.service.dto.DiscordAccountInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkRequestResult
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkResult
import io.github.valine3gdev.mcguildlink.app.service.dto.MinecraftAccountInfo
import io.github.valine3gdev.mcguildlink.app.testutil.createLink
import io.github.valine3gdev.mcguildlink.app.testutil.createTestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid


class AccountLinkServiceTest {
    @Test
    fun `getOrCreateLinkRequest reuses existing code and updates username`() = runBlocking {
        val db = createTestDatabase()
        val service = AccountLinkService(db)

        val firstRequest = assertIs<LinkRequestResult.Success>(
            service.getOrCreateLinkRequest(1u, "discord-old")
        )
        val secondRequest = assertIs<LinkRequestResult.Success>(
            service.getOrCreateLinkRequest(1u, "discord-new")
        )

        assertEquals(firstRequest.code, secondRequest.code)

        transaction(db) {
            val discordAccounts = DiscordAccountEntity.all().toList()
            val linkRequests = LinkRequestEntity.all().toList()

            assertEquals(1, discordAccounts.size)
            assertEquals(1, linkRequests.size)
            assertEquals("discord-new", discordAccounts.single().lastKnownUsername)
            assertEquals(firstRequest.code, linkRequests.single().code)
        }
    }

    @Test
    fun `consumeCodeAndLink persists link removes request and exposes lookup apis`() = runBlocking {
        val db = createTestDatabase()
        val service = AccountLinkService(db)
        val minecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000301")
        val code = assertIs<LinkRequestResult.Success>(
            service.getOrCreateLinkRequest(1u, "discord-1")
        ).code

        val result = assertIs<LinkResult.Success>(
            service.consumeCodeAndLink(code, minecraftUuid, "Steve")
        )

        assertEquals(DiscordAccountInfo(1u, "discord-1"), result.discordAccount)
        assertEquals(
            listOf(MinecraftAccountInfo(minecraftUuid, "Steve")),
            service.getLinkedMinecraftAccounts(1u)
        )
        assertEquals(
            listOf(DiscordAccountInfo(1u, "discord-1")),
            service.getLinkedDiscordAccounts(minecraftUuid)
        )
        assertEquals(
            DiscordAccountInfo(1u, "discord-1") to MinecraftAccountInfo(minecraftUuid, "Steve"),
            service.getLinkOrNull(1u, minecraftUuid)
        )

        transaction(db) {
            assertTrue(LinkRequestEntity.all().toList().isEmpty())
            assertEquals(1, AccountLinkEntity.all().toList().size)
            assertEquals("discord-1", DiscordAccountEntity.all().single().lastKnownUsername)
            assertEquals("Steve", MinecraftAccountEntity.all().single().lastKnownName)
        }
    }

    @Test
    fun `consumeCodeAndLink returns invalid code when request is missing`() {
        val service = AccountLinkService(createTestDatabase())

        assertIs<LinkResult.InvalidCode>(
            service.consumeCodeAndLink(
                "missing",
                Uuid.parse("00000000-0000-0000-0000-000000000302"),
                "Steve",
            )
        )
    }

    @Test
    fun `consumeCodeAndLink returns already linked when relation exists`() = runBlocking {
        val db = createTestDatabase()
        val service = AccountLinkService(db)
        val minecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000303")

        createLink(service, 1u, "discord-1", minecraftUuid, "Steve")
        val secondCode = assertIs<LinkRequestResult.Success>(
            service.getOrCreateLinkRequest(1u, "discord-1")
        ).code

        assertIs<LinkResult.AlreadyLinked>(
            service.consumeCodeAndLink(secondCode, minecraftUuid, "Steve")
        )

        transaction(db) {
            assertEquals(1, AccountLinkEntity.all().toList().size)
            assertEquals(1, LinkRequestEntity.all().toList().size)
        }
    }

    @Test
    fun `list methods return links in descending linkedAt order`() = runBlocking {
        val db = createTestDatabase()
        val service = AccountLinkService(db)
        val firstMinecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000304")
        val secondMinecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000305")

        createLink(service, 1u, "discord-1", firstMinecraftUuid, "Steve")
        createLink(service, 1u, "discord-1", secondMinecraftUuid, "Alex")
        createLink(service, 2u, "discord-2", firstMinecraftUuid, "Steve")

        setLinkedAt(db, 1u, firstMinecraftUuid, Instant.parse("2026-01-01T00:00:00Z"))
        setLinkedAt(db, 2u, firstMinecraftUuid, Instant.parse("2026-01-02T00:00:00Z"))
        setLinkedAt(db, 1u, secondMinecraftUuid, Instant.parse("2026-01-03T00:00:00Z"))

        assertEquals(
            listOf(secondMinecraftUuid, firstMinecraftUuid),
            service.listLinksByDiscord(1u).map { it.minecraftAccount.uuid }
        )
        assertEquals(
            listOf(2uL, 1uL),
            service.listLinksByMinecraft(firstMinecraftUuid).map { it.discordAccount.userId }
        )
        assertEquals(
            listOf(
                1uL to secondMinecraftUuid,
                2uL to firstMinecraftUuid,
                1uL to firstMinecraftUuid,
            ),
            service.listAllLinks().map { it.discordAccount.userId to it.minecraftAccount.uuid }
        )
    }

    @Test
    fun `blocked accounts are rejected for request creation and link consumption`(): Unit = runBlocking {
        val db = createTestDatabase()
        val linkService = AccountLinkService(db)
        val blockService = AccountBlockService(db)
        val blockedMinecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000306")

        assertIs<BlockResult.Success>(blockService.blockDiscordAccount(1u, "blocked-user"))
        assertIs<LinkRequestResult.Blocked>(
            linkService.getOrCreateLinkRequest(1u, "blocked-user")
        )

        createLink(linkService, 9u, "discord-9", blockedMinecraftUuid, "BlockedMc")
        assertIs<BlockResult.Success>(blockService.blockDiscordAccount(9u, "discord-9"))

        val openCode = assertIs<LinkRequestResult.Success>(
            linkService.getOrCreateLinkRequest(2u, "discord-2")
        ).code

        assertIs<LinkResult.Blocked>(
            linkService.consumeCodeAndLink(openCode, blockedMinecraftUuid, "BlockedMc")
        )
    }

    @Test
    fun `unlink methods remove links from persistent state`() = runBlocking {
        val db = createTestDatabase()
        val service = AccountLinkService(db)
        val firstMinecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000307")
        val secondMinecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000308")

        createLink(service, 1u, "discord-1", firstMinecraftUuid, "Steve")
        createLink(service, 1u, "discord-1", secondMinecraftUuid, "Alex")

        assertTrue(service.unlink(1u, firstMinecraftUuid))
        assertFalse(service.unlink(1u, firstMinecraftUuid))
        assertNull(service.getLinkOrNull(1u, firstMinecraftUuid))
        assertEquals(
            listOf(secondMinecraftUuid),
            service.getLinkedMinecraftAccounts(1u).map { it.uuid }
        )

        assertEquals(
            listOf(MinecraftAccountInfo(secondMinecraftUuid, "Alex")),
            service.unlinkByDiscord(1u)
        )
        assertTrue(service.unlinkByDiscord(1u).isEmpty())
        assertTrue(service.getLinkedMinecraftAccounts(1u).isEmpty())
        assertTrue(service.listAllLinks().isEmpty())

        transaction(db) {
            assertTrue(AccountLinkEntity.all().toList().isEmpty())
        }
    }
}

private fun setLinkedAt(
    db: Database,
    discordUserId: ULong,
    minecraftUuid: Uuid,
    linkedAt: Instant,
) {
    transaction(db) {
        val link = requireNotNull(AccountStore.getAccountLinkOrNull(discordUserId, minecraftUuid))
        link.linkedAt = linkedAt
    }
}
