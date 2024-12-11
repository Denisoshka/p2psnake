package d.zhdanov.ccfit.nsu.core.network.core.states.events

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage.RoleChangeMsg
import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.net.InetSocketAddress

sealed class Event {
  sealed class State : Event() {
    sealed class ByController : State() {
      data object SwitchToLobby : ByController()
      
      data class JoinReq(
        val gameAnnouncement: GameAnnouncement,
        val playerName: String,
        val playerRole: NodeRole,
        val playerType: PlayerType
      ) : ByController()
      
      data class LaunchGame(
        val internalGameConfig: InternalGameConfig,
      ) : ByController()
    }
    
    sealed class ByInternal : State() {
      data class JoinReqAck(
        val onEventAck: ByController.JoinReq,
        val senderId: Int,
        val gamePlayerInfo: GamePlayerInfo,
        val internalGameConfig: InternalGameConfig,
      ) : ByInternal()
      
      data class MasterNow(
        val gamePlayerInfo: GamePlayerInfo,
        val gameState: SnakesProto.GameMessage.StateMsg,
        val internalGameConfig: InternalGameConfig,
      ) : ByInternal()
    }
  }
  
  
  sealed class InternalGameEvent : Event() {
    data class NewState(
      val state: StateMsg,
    ) : State()
    
    data class NewNodeRegistered(
      val clusterNodeT: ClusterNodeT<Node.MsgInfo>,
    ) : State()
    
    data class RoleChanged(
      val roleChangeMsg: RoleChangeMsg
    )
    
    data class DeputyMasterNow(
      val newMaster: Pair<InetSocketAddress, Int>
    )
  }
}