package io.github.valine3gdev.mcguildlink.app.minecraft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.config.ServerConfig
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogInput
import net.minestom.server.dialog.DialogMetadata
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerConfigCustomClickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap


val CODE_SUBMIT_KEY = Key.key("mcguildlink:submit_code")
val DIALOG_SUCCESS_KEY = Key.key("mcguildlink:dialog_success")

/**
 * 入力待ちが溜まらないように、コード入力のタイムアウトを設定する。
 */
val CODE_INPUT_TIMEOUT: Duration = Duration.ofMinutes(5)


private val logger = KotlinLogging.logger {}


class Server(
    private val config: ServerConfig
) {
    fun start() {
        val minecraftServer = MinecraftServer.init(Auth.Online())
        val pendingConfigurationDisconnects = ConcurrentHashMap<UUID, CompletableFuture<Unit>>()

        val globalEventHandler = MinecraftServer.getGlobalEventHandler()
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player

            val disconnectFuture = CompletableFuture<Unit>()
            pendingConfigurationDisconnects[player.uuid] = disconnectFuture
            val timeoutTask = MinecraftServer.getSchedulerManager()
                .buildTask {
                    if (disconnectFuture.isDone || !player.isOnline) {
                        return@buildTask
                    }
                    logger.warn { "Player ${player.username} (${player.uuid}) timed out during authentication. Disconnecting." }
                    player.kick("タイムアウトしました。再接続してください。")
                }
                .delay(CODE_INPUT_TIMEOUT)
                .schedule()

            showCodeDialog(player)

            try {
                disconnectFuture.join()
            } finally {
                timeoutTask.cancel()
                pendingConfigurationDisconnects.remove(player.uuid, disconnectFuture)
            }
        }

        globalEventHandler.addListener(PlayerConfigCustomClickEvent::class.java) { event ->
            when (event.key) {
                DIALOG_SUCCESS_KEY -> {
                    logger.info { "Player ${event.player.username} (${event.player.uuid}) completed authentication successfully. Disconnecting." }
                    event.player.kick("正常に切断されました。")
                }

                CODE_SUBMIT_KEY -> {
                    val payload = event.payload as? CompoundBinaryTag ?: run {
                        showCodeDialog(
                            event.player,
                            errorMessage = Component.text("コードを受け取れませんでした。もう一度入力してください。")
                        )
                        logger.error { "Player ${event.player.username} (${event.player.uuid}) submitted invalid payload for code submission." }
                        return@addListener
                    }

                    val code = payload.getString("code").trim()
                    if (code.isEmpty()) {
                        showCodeDialog(
                            event.player,
                            errorMessage = Component.text("コードが空です。もう一度入力してください。")
                        )
                        return@addListener
                    }

                    if (!confirmCode(code)) {
                        showCodeDialog(
                            event.player,
                            initialCode = code,
                            errorMessage = Component.text("コードを確認できませんでした。もう一度入力してください。")
                        )
                        return@addListener
                    }

                    showSuccessDialog(event.player, code)
                }

                else -> return@addListener
            }
        }

        globalEventHandler.addListener(PlayerDisconnectEvent::class.java) { event ->
            pendingConfigurationDisconnects.remove(event.player.uuid)?.complete(Unit)
        }

        minecraftServer.start(config.address, config.port)
    }
}


private fun showCodeDialog(
    player: Player,
    initialCode: String = "",
    errorMessage: Component? = null
) {
    val body = buildList {
        add(
            DialogBody.PlainMessage(
                Component.join(
                    JoinConfiguration.newlines(), listOf(
                        Component.text("発行されたコードを以下に入力してください。"),
                        Component.text("${CODE_INPUT_TIMEOUT.toMinutes()}分以内に入力する必要があります。")
                    )
                ),
                256
            )
        )
        errorMessage?.let {
            add(DialogBody.PlainMessage(it, 256))
        }
    }

    player.showDialog(
        Dialog.Notice(
            DialogMetadata(
                Component.text("アカウント認証"),
                null,
                false,
                true,
                DialogAfterAction.CLOSE,
                body,
                listOf(
                    DialogInput.Text(
                        "code",
                        120,
                        Component.text("コード"),
                        true,
                        initialCode,
                        8,
                        null
                    )
                )
            ),
            DialogActionButton(
                Component.text("送信"),
                null,
                120,
                DialogAction.DynamicCustom(CODE_SUBMIT_KEY, null)
            )
        )
    )
}

private fun showSuccessDialog(player: Player, code: String) {
    player.showDialog(
        Dialog.Notice(
            DialogMetadata(
                Component.text("認証成功しました。"),
                null,
                false,
                true,
                DialogAfterAction.CLOSE,
                listOf(
                    DialogBody.PlainMessage(
                        Component.text("$code (後々 Discordアカウント名に変える) との紐付けが完了しました。"),
                        256
                    )
                ),
                emptyList()
            ),
            DialogActionButton(
                Component.text("切断"),
                null,
                120,
                DialogAction.Custom(DIALOG_SUCCESS_KEY, null)
            )
        )
    )
}

private fun confirmCode(code: String): Boolean {
    // TODO: Replace this with the actual Discord-side confirmation.
    return code.length == 8
}
