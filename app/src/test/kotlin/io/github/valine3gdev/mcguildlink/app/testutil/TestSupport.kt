package io.github.valine3gdev.mcguildlink.app.testutil

import io.github.valine3gdev.mcguildlink.app.db.DatabaseFactory
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import io.github.valine3gdev.mcguildlink.app.service.WhitelistRefreshRequester
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkRequestResult
import io.github.valine3gdev.mcguildlink.app.service.dto.LinkResult
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class TestEnvironment {
    val root: Path = createTempDirectory("mcguildlink-test-")
    val db: Database = DatabaseFactory.connect(root.resolve("app.db"))
    val whitelistFile: Path = root.resolve("static/whitelist.json")
}

fun createTestDatabase(): Database =
    DatabaseFactory.connect(createTempDirectory("mcguildlink-test-").resolve("app.db"))

class CountingWhitelistRefreshRequester : WhitelistRefreshRequester {
    var count: Int = 0

    override fun requestRefresh() {
        count += 1
    }
}

suspend fun createLink(
    linkService: AccountLinkService,
    discordUserId: ULong,
    discordUsername: String,
    minecraftUuid: Uuid,
    minecraftName: String,
): LinkResult.Success {
    val request = assertIs<LinkRequestResult.Success>(
        linkService.getOrCreateLinkRequest(discordUserId, discordUsername)
    )

    return assertIs(
        linkService.consumeCodeAndLink(request.code, minecraftUuid, minecraftName)
    )
}
