package io.github.valine3gdev.mcguildlink.app.config

import dev.eav.tomlkt.Toml
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration


@Serializable
data class Config(
    val bot: BotConfig,

    @SerialName("minecraft_server")
    val minecraftServer: MinecraftServerConfig,
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
data class MinecraftServerConfig(
    val address: String,
    val port: Int,
    val timeout: Duration,
)
