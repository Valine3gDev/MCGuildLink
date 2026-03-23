package io.github.valine3gdev.mcguildlink.app.util

import kotlin.random.Random


interface LinkCodeGenerator {
    fun generate(): String
}


class RandomLinkCodeGenerator(
    private val length: Int = 8,
    private val random: Random = Random.Default,
) : LinkCodeGenerator {
    companion object {
        const val CHARS = "ACDEFGHJKMNPQRTUVWXYZacdefghjkmnpqrtuvwxyz234679"
    }

    override fun generate(): String {
        return buildString(length) {
            repeat(length) {
                append(CHARS.random(random))
            }
        }
    }
}
