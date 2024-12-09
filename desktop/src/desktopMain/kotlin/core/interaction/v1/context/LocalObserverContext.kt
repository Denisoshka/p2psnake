package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine

class LocalObserverContext(
  override val name: String,
  private val snake: SnakeEntity,
  private var lastUpdateSeq: Long = 0L,
  private val ncStateMachine: NetworkStateMachine
) : NodePayloadT, Entity by snake {
  @Synchronized
  override fun handleEvent(event: SteerMsg, seq: Long) {
    if(seq <= lastUpdateSeq) return

    lastUpdateSeq = seq
    snake.changeState(event.direction)
  }

  override fun onContextObserverTerminated() {
    snake.snakeState = SnakeState.ZOMBIE
  }

  override fun shootContextState(state: StateMsg) {
    val pl = GamePlayer(
      name,
      ncStateMachine.internalNodeId,
      null,
      null,
      NodeRole.MASTER,
      PlayerType.HUMAN,
      snake.score
    )
    state.players.add(pl)
  }

  override fun atDead(context: GameEngine) {
    snake.atDead(context)
    TODO(
      "а что я вообще хотел тут сделать?, а ну мы же " +
        "умерли нужно просто уведомить кластер"
    )
  }
}