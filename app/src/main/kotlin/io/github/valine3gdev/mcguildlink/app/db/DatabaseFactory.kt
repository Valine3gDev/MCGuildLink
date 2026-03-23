package io.github.valine3gdev.mcguildlink.app.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import java.sql.Connection


// TODO: DBのマイグレーションを実装する
object DatabaseFactory {
    fun connect(dbFile: Path): Database {
        return Database.connect(
            url = "jdbc:sqlite:${dbFile.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        ).also {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

            transaction(it) {
                SchemaUtils.create(DiscordAccounts, MinecraftAccounts, AccountLinks, LinkRequests)
            }
        }
    }
}
