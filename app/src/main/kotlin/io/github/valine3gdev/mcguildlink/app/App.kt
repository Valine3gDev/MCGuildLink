package io.github.valine3gdev.mcguildlink.app

import io.github.valine3gdev.mcguildlink.app.config.Config
import io.github.valine3gdev.mcguildlink.app.db.DatabaseFactory
import io.github.valine3gdev.mcguildlink.app.discord.Bot
import io.github.valine3gdev.mcguildlink.app.minecraft.MinecraftServer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


class App(
    paths: AppPaths = AppPaths.detect()
) {
    private val config = Config.load(paths.configFile)

    private val db = DatabaseFactory.connect(paths.dbFile)

    val bot = Bot(
        config = config.bot,
    )

    val minecraftServer = MinecraftServer(
        config = config.minecraftServer,
    )

    suspend fun start() = coroutineScope {
        minecraftServer.start()

        launch { bot.start() }
    }
}

suspend fun main() {
    val app = App()
    app.start()
}
