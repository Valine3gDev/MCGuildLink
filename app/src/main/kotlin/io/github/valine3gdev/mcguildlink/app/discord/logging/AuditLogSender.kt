package io.github.valine3gdev.mcguildlink.app.discord.logging

import dev.kord.common.Color
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.entity.MessageFlag
import dev.kord.common.entity.Snowflake
import dev.kord.common.toMessageFormat
import dev.kord.core.Kord
import dev.kord.core.entity.User
import dev.kord.rest.builder.component.ContainerBuilder
import dev.kord.rest.builder.component.section
import dev.kord.rest.builder.component.separator
import dev.kord.rest.builder.message.container
import dev.kord.rest.builder.message.messageFlags
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.valine3gdev.mcguildlink.app.service.dto.DiscordAccountInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.MinecraftAccountInfo
import io.github.valine3gdev.mcguildlink.app.service.dto.toHeadAvatarUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant


private val logger = KotlinLogging.logger {}

interface AuditLogSender {
    fun send(timestamp: Instant = Clock.System.now(), entry: ContainerBuilder.() -> Unit)

    fun sendInfo(timestamp: Instant = Clock.System.now(), entry: ContainerBuilder.() -> Unit) = send(timestamp) {
        entry.invoke(this)
        this.accentColor = Color(87, 242, 135)
    }

    fun sendWarn(timestamp: Instant = Clock.System.now(), entry: ContainerBuilder.() -> Unit) = send(timestamp) {
        entry.invoke(this)
        this.accentColor = Color(254, 231, 92)
    }

    fun sendError(timestamp: Instant = Clock.System.now(), entry: ContainerBuilder.() -> Unit) = send(timestamp) {
        entry.invoke(this)
        this.accentColor = Color(237, 66, 69)
    }
}

class DiscordAuditLogSender(
    private val kord: Kord,
    private val channelId: Snowflake,
    private val scope: CoroutineScope,
) : AuditLogSender {
    override fun send(timestamp: Instant, entry: ContainerBuilder.() -> Unit) {
        scope.launch {
            try {
                kord.rest.channel.createMessage(channelId) {
                    messageFlags {
                        +MessageFlag.IsComponentsV2
                    }

                    container {
                        entry.invoke(this)
                        separator {
                            divider = false
                        }
                        textDisplay("-# ${timestamp.toMessageFormat(DiscordTimestampStyle.LongDateTime)}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to send audit log to channel $channelId." }
            }
        }
    }
}

fun AuditLogSender.sendLinkSucceeded(discordAccount: DiscordAccountInfo, minecraftAccount: MinecraftAccountInfo) =
    sendInfo {
        textDisplay("下記のアカウントの紐付けが完了しました。")

        separator {
            divider = false
        }

        section {
            textDisplay(
                """
                - Discordユーザー
                  - ${discordAccount.lastKnownUsername} (`${discordAccount.userId}`)
                - Minecraftアカウント
                  - ${minecraftAccount.lastKnownName} (`${minecraftAccount.uuid}`)
                """.trimIndent()
            )

            thumbnailAccessory {
                url = minecraftAccount.toHeadAvatarUrl()
            }
        }
    }

fun AuditLogSender.sendMemberLeaveUnlinked(leftUser: User, minecraftAccounts: List<MinecraftAccountInfo>) =
    sendInfo {
        textDisplay("メンバーの退出を検知したため、下記アカウントの紐付けを自動解除しました。")

        separator {
            divider = false
        }

        val accountListText = minecraftAccounts.joinToString("\n") { "  - ${it.lastKnownName} (`${it.uuid}`)" }
        textDisplay(
            """
            |- Discordユーザー
            |  - ${leftUser.username} (`${leftUser.id}`)
            |- Minecraftアカウント
            |$accountListText
            """.trimMargin()
        )
    }

fun AuditLogSender.sendMemberBannedBlocked(leftUser: User) =
    sendInfo {
        textDisplay("メンバーのBANを検知したため、関連アカウントを自動ブロックしました。")

        separator {
            divider = false
        }

        textDisplay(
            """
            - Discordユーザー
              - ${leftUser.username} (`${leftUser.id}`)
            """.trimIndent()
        )
    }
