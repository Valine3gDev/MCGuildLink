package io.github.valine3gdev.mcguildlink.app.service

import io.github.valine3gdev.mcguildlink.app.db.DatabaseFactory
import io.github.valine3gdev.mcguildlink.app.service.dto.BlockResult
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkRequestResult
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkResult
import io.github.valine3gdev.mcguildlink.app.service.dto.UnblockResult
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid


class WhitelistRefreshRequesterIntegrationTest {
    @Test
    fun `account link service requests refresh only for successful link changes`() = runBlocking {
        val requester = CountingWhitelistRefreshRequester()
        val db = DatabaseFactory.connect(createTempDirectory("mcguildlink-test-").resolve("app.db"))
        val service = AccountLinkService(
            db = db,
            whitelistRefreshRequester = requester,
        )
        val minecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000101")

        assertIs<LinkRequestResult.Success>(service.getOrCreateLinkRequest(1u, "discord-1"))
        assertEquals(0, requester.count)

        assertIs<LinkResult.InvalidCode>(service.consumeCodeAndLink("invalid", minecraftUuid, "MiXeDCaSe"))
        assertEquals(0, requester.count)

        val firstCode = assertIs<LinkRequestResult.Success>(service.getOrCreateLinkRequest(1u, "discord-1")).code
        assertIs<LinkResult.Success>(service.consumeCodeAndLink(firstCode, minecraftUuid, "MiXeDCaSe"))
        assertEquals(1, requester.count)

        val secondCode = assertIs<LinkRequestResult.Success>(service.getOrCreateLinkRequest(1u, "discord-1")).code
        assertIs<LinkResult.AlreadyLinked>(service.consumeCodeAndLink(secondCode, minecraftUuid, "MiXeDCaSe"))
        assertEquals(1, requester.count)

        assertEquals(false, service.unlink(1u, Uuid.parse("00000000-0000-0000-0000-000000000102")))
        assertEquals(1, requester.count)

        assertEquals(true, service.unlink(1u, minecraftUuid))
        assertEquals(2, requester.count)

        val thirdCode = assertIs<LinkRequestResult.Success>(service.getOrCreateLinkRequest(1u, "discord-1")).code
        assertIs<LinkResult.Success>(
            service.consumeCodeAndLink(
                thirdCode,
                Uuid.parse("00000000-0000-0000-0000-000000000103"),
                "AnotherName",
            )
        )
        assertEquals(3, requester.count)

        assertEquals(true, service.unlinkByDiscord(1u))
        assertEquals(4, requester.count)

        assertEquals(false, service.unlinkByDiscord(1u))
        assertEquals(4, requester.count)
    }

    @Test
    fun `account block service requests refresh only for successful block changes`() = runBlocking {
        val requester = CountingWhitelistRefreshRequester()
        val db = DatabaseFactory.connect(createTempDirectory("mcguildlink-test-").resolve("app.db"))
        val linkService = AccountLinkService(db)
        val service = AccountBlockService(
            db = db,
            whitelistRefreshRequester = requester,
        )
        val minecraftUuid = Uuid.parse("00000000-0000-0000-0000-000000000201")

        val code = assertIs<LinkRequestResult.Success>(
            linkService.getOrCreateLinkRequest(1u, "discord-1")
        ).code
        assertIs<LinkResult.Success>(linkService.consumeCodeAndLink(code, minecraftUuid, "BlockedName"))

        assertIs<BlockResult.Success>(service.blockDiscordAccount(1u, "discord-1"))
        assertEquals(1, requester.count)

        assertIs<BlockResult.AlreadyBlocked>(service.blockDiscordAccount(1u, "discord-1"))
        assertEquals(1, requester.count)

        assertIs<UnblockResult.Success>(service.unblockDiscordAccount(1u))
        assertEquals(1, requester.count)

        assertIs<UnblockResult.NotBlocked>(service.unblockDiscordAccount(1u))
        assertEquals(1, requester.count)
    }
}


private class CountingWhitelistRefreshRequester : WhitelistRefreshRequester {
    var count: Int = 0

    override fun requestRefresh() {
        count += 1
    }
}
