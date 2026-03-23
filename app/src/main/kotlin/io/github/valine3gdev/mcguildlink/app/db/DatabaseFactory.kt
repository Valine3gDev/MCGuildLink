package io.github.valine3gdev.mcguildlink.app.db

import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path


object DatabaseFactory {
    fun connect(dbFile: Path): Database {
        return Database.connect(
            url = "jdbc:sqlite:${dbFile.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
    }
}
