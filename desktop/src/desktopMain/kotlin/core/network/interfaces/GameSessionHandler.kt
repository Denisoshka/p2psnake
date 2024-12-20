package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core2.states.Event

interface GameSessionHandler {
  fun handleJoinToGameReq(joinReq: Event.State.ByController.JoinReq)
  fun handleLaunchGame(
    launchGameReq: Event.State.ByController.LaunchGame
  )
  
  fun handleConnectToGame(
    joinReqAck: Event.State.ByInternal.JoinReqAck
  )
  
  fun handleSwitchToLobby(
    switchToLobbyReq: Event.State.ByController.SwitchToLobby
  )
  
  fun handleSendStateToController(state: SnakesProto.GameState)
}