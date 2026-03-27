package io.github.valine3gdev.mcguildlink.app

import io.github.valine3gdev.mcguildlink.app.config.Config
import io.github.valine3gdev.mcguildlink.app.db.DatabaseFactory
import io.github.valine3gdev.mcguildlink.app.discord.Bot
import io.github.valine3gdev.mcguildlink.app.minecraft.MinecraftServer
import io.github.valine3gdev.mcguildlink.app.service.AccountBlockService
import io.github.valine3gdev.mcguildlink.app.service.AccountLinkService
import io.github.valine3gdev.mcguildlink.app.service.WhitelistFileSyncService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


class App(
    paths: AppPaths = AppPaths.detect()
) {
    private val config = Config.load(paths.configFile)

    private val db = DatabaseFactory.connect(paths.dbFile)

    private val whitelistFileSyncService = WhitelistFileSyncService(
        db = db,
        whitelistFile = paths.whitelistFile,
    )

    private val accountLinkService = AccountLinkService(
        db = db,
        whitelistRefreshRequester = whitelistFileSyncService,
    )
    private val accountBlockService = AccountBlockService(
        db = db,
        whitelistRefreshRequester = whitelistFileSyncService,
    )

    val bot = Bot(
        config = config.bot,
        accountLinkService = accountLinkService,
        accountBlockService = accountBlockService,
    )

    val minecraftServer = MinecraftServer(
        config = config.minecraftServer,
        accountLinkService = accountLinkService,
    )

    suspend fun start() = coroutineScope {
        whitelistFileSyncService.generateNow()
        whitelistFileSyncService.attach(this)

        minecraftServer.start()

        launch { bot.start() }
    }
}

suspend fun main() {
    val app = App()
    app.start()
}
