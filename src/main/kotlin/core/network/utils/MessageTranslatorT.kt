package d.zhdanov.ccfit.nsu.core.network.utils

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

interface MessageTranslatorT<MessageT> {
  fun getMessageType(message: MessageT): MessageType

  fun fromMessageT(msg: MessageT): P2PMessage
  fun fromMessageT(message: MessageT, msgT: MessageType): P2PMessage

  fun toMessageT(msg: P2PMessage): MessageT
  fun toMessageT(msg: P2PMessage, msgT: MessageType): MessageT
}