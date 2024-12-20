package d.zhdanov.ccfit.nsu.controllers.dto

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg
import java.time.Instant

data class AnnouncementInfo(
  val msg: SnakesProto.GameAnnouncement,
  var timestamp: Instant = Instant.now()
)