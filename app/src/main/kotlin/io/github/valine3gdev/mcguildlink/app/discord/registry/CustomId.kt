package io.github.valine3gdev.mcguildlink.app.discord.registry

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User
import kotlin.uuid.Uuid


sealed interface CustomId {
    fun matches(id: String): Boolean
}

interface CustomIdParser<T> {
    fun parse(id: String): T?
}

class EqualsCustomId(private val value: String) : CustomId {
    override fun matches(id: String): Boolean = value == id
}

class PrefixCustomId<T>(
    private val prefix: String,
    private val parser: ((String) -> T)? = null
) : CustomId, CustomIdParser<T> {
    override fun matches(id: String): Boolean = id.startsWith(prefix)

    override fun parse(id: String): T? = parser?.invoke(id.removePrefix(prefix))
}

fun createLinkedCustomId(prefix: String) = PrefixCustomId(prefix) { data ->
    data.split("/").takeIf { it.size == 2 }?.let {
        val (userIdStr, uuidStr) = it
        val uuid = Uuid.parseHexDash(uuidStr)
        val userId = Snowflake(userIdStr.toULong())
        userId to uuid
    }
}

fun createLinkedCustomIdString(prefix: String, user: User, mcUuid: Uuid) =
    "${prefix}${user.id.value}/${mcUuid.toHexDashString()}"
