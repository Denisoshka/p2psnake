package d.zhdanov.ccfit.nsu.core.interaction.v1.messages

data class GameConfig(
  var width: Int = 40,
  var height: Int = 30,
  var foodStatic: Int = 1,
  var stateDelayMs: Int = 3000,
)