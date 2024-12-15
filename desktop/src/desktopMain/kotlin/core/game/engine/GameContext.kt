package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

interface GameContext {
  val gameMap: GameMap
  val entities: MutableList<Entity>
  val sideEffectEntity: MutableList<Entity>
  fun launch()
  fun shutdown()
  
  fun addSideEntity(entity: Entity)
  
  fun initGame(
    config: GameConfig,
    gamePlayerInfo: GamePlayerInfo
  ): List<ObservableSnakeEntity>
  
  fun initGame(
    config: GameConfig,
    playerInfo: GamePlayerInfo,
    state: SnakesProto.GameMessage.StateMsg
  ): List<ObservableSnakeEntity>
}