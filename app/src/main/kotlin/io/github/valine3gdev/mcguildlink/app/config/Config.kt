package io.github.valine3gdev.mcguildlink.app.config

import dev.eav.tomlkt.Toml
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import kotlin.io.path.readText


@Serializable
data class Config(
    val bot: BotConfig,
    val server: ServerConfig,
) {
    companion object {
        fun load(path: Path) = Toml.decodeFromString<Config>(path.readText())

    }
}


@Serializable
data class BotConfig(
    val token: String,
    val guild: Snowflake,
)


@Serializable
data class ServerConfig(
    val address: String,
    val port: Int,
)
