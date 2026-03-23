package io.github.valine3gdev.mcguildlink.app.minecraft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.config.ServerConfig
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.dialog.*
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerConfigCustomClickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.toJavaDuration


private val logger = KotlinLogging.logger {}


class Server(
    private val config: ServerConfig
) {
    companion object {
        private val CODE_SUBMIT_KEY = Key.key("mcguildlink:submit_code")
        private val DIALOG_SUCCESS_KEY = Key.key("mcguildlink:dialog_success")
        private const val CODE_INPUT_NBT_KEY = "code"
    }

    fun start() {
        val minecraftServer = MinecraftServer.init(Auth.Online())

        val pendingConfigurationDisconnects = ConcurrentHashMap<UUID, CompletableFuture<Unit>>()

        val globalEventHandler = MinecraftServer.getGlobalEventHandler()
        globalEventHandler.addListener(PlayerConfigCustomClickEvent::class.java, ::handlePlayerConfigCustomClickEvent)
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) {
            handleAsyncPlayerConfigurationEvent(pendingConfigurationDisconnects, it)
        }
        globalEventHandler.addListener(PlayerDisconnectEvent::class.java) { event ->
            pendingConfigurationDisconnects.remove(event.player.uuid)?.complete(Unit)
        }

        minecraftServer.start(config.address, config.port)
    }

    private fun handleAsyncPlayerConfigurationEvent(
        pendingConfigurationDisconnects: ConcurrentHashMap<UUID, CompletableFuture<Unit>>,
        event: AsyncPlayerConfigurationEvent
    ) {
        val player = event.player

        val disconnectFuture = CompletableFuture<Unit>()
        pendingConfigurationDisconnects[player.uuid] = disconnectFuture
        val timeoutTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                if (disconnectFuture.isDone || !player.isOnline) {
                    return@buildTask
                }
                logger.warn { "Player ${player.username} (${player.uuid}) timed out during authentication. Disconnecting." }
                player.kick(
                    """
                    コードを入力する時間が長過ぎたため、切断されました。
                    もう一度接続してコードを入力してください。
                    すでにコードを発行している場合、コードの再発行は不要です。
                    """.trimIndent()
                )
            }
            .delay(config.timeout.toJavaDuration())
            .schedule()

        showCodeDialog(player)

        try {
            disconnectFuture.join()
        } finally {
            timeoutTask.cancel()
            pendingConfigurationDisconnects.remove(player.uuid, disconnectFuture)
        }
    }

    private fun handlePlayerConfigCustomClickEvent(event: PlayerConfigCustomClickEvent) {
        when (event.key) {
            DIALOG_SUCCESS_KEY -> {
                logger.info { "Player ${event.player.username} (${event.player.uuid}) completed authentication successfully. Disconnecting." }
                event.player.kick("正常に切断されました。")
            }

            CODE_SUBMIT_KEY -> {
                val payload = event.payload as? CompoundBinaryTag ?: run {
                    showCodeDialog(
                        event.player,
                        errorMessage = "コードを受け取れませんでした。もう一度入力してください。"
                    )
                    logger.error { "Player ${event.player.username} (${event.player.uuid}) submitted invalid payload for code submission." }
                    return
                }

                val code = payload.getString(CODE_INPUT_NBT_KEY).trim()
                if (code.isEmpty()) {
                    showCodeDialog(
                        event.player,
                        errorMessage = "コードが空です。もう一度入力してください。"
                    )
                    return
                }

                if (!confirmCode(code)) {
                    showCodeDialog(
                        event.player,
                        initialCode = code,
                        errorMessage = "コードを確認できませんでした。もう一度入力してください。"
                    )
                    return
                }

                showSuccessDialog(event.player, code)
            }

            else -> return
        }
    }

    private fun buildDialog(
        title: String,
        actionLabel: String,
        actionKey: Key,
        bodyMessages: List<String>,
        inputs: List<DialogInput> = emptyList(),
    ): Dialog {
        return Dialog.Notice(
            DialogMetadata(
                Component.text(title),
                null,
                false,
                true,
                DialogAfterAction.CLOSE,
                listOf(
                    DialogBody.PlainMessage(
                        Component.join(
                            JoinConfiguration.newlines(),
                            bodyMessages.map(Component::text)
                        ),
                        256
                    )
                ),
                inputs
            ),
            DialogActionButton(
                Component.text(actionLabel),
                null,
                128,
                DialogAction.DynamicCustom(actionKey, null)
            )
        )
    }

    private fun showCodeDialog(
        player: Player,
        initialCode: String = "",
        errorMessage: String? = null
    ) {
        player.showDialog(
            buildDialog(
                "アカウント認証", "送信", CODE_SUBMIT_KEY,
                buildList {
                    add("発行されたコードを以下に入力してください。")
                    errorMessage?.let { add(it) }
                },
                listOf(
                    DialogInput.Text(
                        CODE_INPUT_NBT_KEY,
                        128,
                        Component.text("コード"),
                        true,
                        initialCode,
                        8,
                        null
                    )
                )
            )
        )
    }

    private fun showSuccessDialog(player: Player, code: String) {
        player.showDialog(
            buildDialog(
                "成功しました。",
                "切断",
                DIALOG_SUCCESS_KEY,
                listOf("$code (後々 Discordアカウント名に変える) との紐付けが完了しました。")
            )
        )
    }

    private fun confirmCode(code: String): Boolean {
        // TODO: Replace this with the actual Discord-side confirmation.
        return code.length == 8
    }
}
