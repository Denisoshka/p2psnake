package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.MessageType

class ErrorMsg(val message: String) : d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.Msg(MessageType.ErrorMsg) {
}
