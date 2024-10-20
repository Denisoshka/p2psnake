package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

class ErrorMsg(val message: String) : GameMessage(0, MessageType.ErrorMsg) {
}
