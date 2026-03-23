package io.github.valine3gdev.mcguildlink.app.util

import java.security.SecureRandom
import java.util.Random


interface LinkCodeGenerator {
    fun generate(): String
}


class RandomLinkCodeGenerator(
    private val length: Int = 8,
    private val random: Random = SecureRandom(),
) : LinkCodeGenerator {
    init {
        require(length > 0) { "Code length must be positive" }
    }

    companion object {
        const val CHARS = "ACDEFGHJKMNPQRTUVWXYZacdefghjkmnpqrtuvwxyz234679"
    }

    override fun generate(): String {
        return CharArray(length) {
            CHARS[random.nextInt(CHARS.length)]
        }.concatToString()
    }
}
