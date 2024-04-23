package dev.vanutp.tgbridge.fabric

import dev.vanutp.tgbridge.common.Platform
import dev.vanutp.tgbridge.common.models.TBCommandContext
import dev.vanutp.tgbridge.common.models.TBPlayerEventData
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.chat.SignedMessage
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Language


class FabricPlatform(private val server: MinecraftServer) : Platform() {
    override val name = "fabric"
    override val configDir = FabricLoader.getInstance().configDir.resolve(FabricTelegramBridge.MOD_ID)

    override fun registerChatMessageListener(handler: (TBPlayerEventData) -> Unit) {
        ServerMessageEvents.CHAT_MESSAGE.register { message: SignedMessage, sender, _ ->
            handler.invoke(
                TBPlayerEventData(
                    sender.displayName.string,
                    message.unsignedContent() ?: Component.text(message.message()),
                )
            )
        }
    }

    private fun registerFilteredPlayerEvent(
        handler: (TBPlayerEventData) -> Unit,
        filter: (TranslatableTextContent) -> Boolean
    ) {
        ServerMessageEvents.GAME_MESSAGE.register { _, text, _ ->
            val content = text.content
            if (content is TranslatableTextContent && filter(content)) {
                handler(
                    TBPlayerEventData((content.args[0] as Text).string, text.asComponent())
                )
            }
        }
    }

    override fun registerPlayerDeathListener(handler: (TBPlayerEventData) -> Unit) {
        registerFilteredPlayerEvent(handler) { it.key.startsWith("death.") }
    }

    override fun registerPlayerJoinListener(handler: (TBPlayerEventData) -> Unit) {
        registerFilteredPlayerEvent(handler) { it.key == "multiplayer.player.joined" }
    }

    override fun registerPlayerLeaveListener(handler: (TBPlayerEventData) -> Unit) {
        registerFilteredPlayerEvent(handler) { it.key == "multiplayer.player.left" }
    }

    override fun registerCommand(command: Array<String>, handler: (TBCommandContext) -> Boolean) {
        // TODO: get rid of code duplication between versions and loaders
        val builder = CommandManager.literal(command[0])
        var lastArg = builder
        command.drop(1).forEachIndexed { i, x ->
            val newArg = CommandManager.literal(x)
            if (i == command.size - 2) {
                newArg.executes { ctx ->
                    val res = handler(TBCommandContext(
                        reply = { text ->
                            ctx.source.sendFeedback(Text.literal(text), false)
                        }
                    ))
                    return@executes if (res) 1 else -1
                }
            }
            lastArg.then(newArg)
            lastArg = newArg
        }
        server.commandManager.dispatcher.register(builder)
    }

    override fun registerPlayerAdvancementListener(handler: (TBPlayerEventData) -> Unit) {
        registerFilteredPlayerEvent(handler) { it.key.startsWith("chat.type.advancement.") }
    }

    override fun broadcastMessage(text: Component) {
        FabricServerAudiences.of(server).all().sendMessage(text)
    }

    override fun getOnlinePlayerNames(): Array<String> = server.playerNames

    override fun getLanguageKey(key: String) = with(Language.getInstance()) {
        if (hasTranslation(key)) {
            get(key)
        } else {
            null
        }
    }
}
