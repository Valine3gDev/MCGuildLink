package io.github.valine3gdev.mcguildlink.app

import java.nio.file.Path
import kotlin.io.path.createDirectories


data class AppPaths(
    val appHome: Path,
    val configFile: Path,
    val dataDir: Path,
    val dbFile: Path,
) {
    companion object {
        fun detect(): AppPaths {
            val codeSource = AppPaths::class.java
                .protectionDomain
                .codeSource
                ?.location
                ?.toURI()

            val appHome = if (codeSource != null) {
                val path = Path.of(codeSource)
                val parent = path.parent
                if (parent != null && parent.fileName?.toString() == "lib") {
                    parent.parent
                } else {
                    Path.of(System.getProperty("user.dir"))
                }
            } else {
                Path.of(System.getProperty("user.dir"))
            }

            val configFile = appHome.resolve("config/app.toml")
            val dataDir = appHome.resolve("data")
            val dbFile = dataDir.resolve("app.db")

            dataDir.createDirectories()

            return AppPaths(
                appHome = appHome,
                configFile = configFile,
                dataDir = dataDir,
                dbFile = dbFile,
            )
        }
    }
}
