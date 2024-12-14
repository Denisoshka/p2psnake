package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ContextEvent
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.StateHolder
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger { Utils::class.java }
private val PortValuesRange = 1..65535

object Utils {
  private suspend fun checkMsInfoInState(
    curMsDp: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    state: SnakesProto.GameState
  ) {
    val stateMs = state.players.playersList.find {
      it.role == SnakesProto.NodeRole.MASTER
    }
    if(stateMs == null) {
      Logger.trace { "master absent in state $state" }
//    switchToLobby(Event.State.ByController.SwitchToLobby)
      return
    }
  }
  
  fun checkDpInfoInState(
    curMsDp: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    state: SnakesProto.GameState
  ): Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>? {
  
  }
  
  fun submitState(
    player: LocalObserverContext,
    stateSeq: Int,
    clusterNodesHandler: ClusterNodesHandler,
    stateHolder: StateHolder,
    state: SnakesProto.GameState.Builder,
  ) {
    val msdp = stateHolder.masterDeputy ?: return
    
    val (ms, dp) = msdp
    player.shootContextState(state, ms, dp)
    
    for((_, node) in clusterNodesHandler) {
      node.payload.shootContextState(state, ms, dp)
    }
    state.apply { stateOrder = stateSeq }.build()
    val stateMsg = SnakesProto.GameMessage.StateMsg.newBuilder().apply {
      setState(state)
    }.build()
    
    for((_, node) in clusterNodesHandler) {
      node.payload ?: continue
      
      val msg = MessageUtils.MessageProducer.getStateMsg(
        stateHolder.nextSeqNum, stateMsg
      )
      
      node.addMessageForAck(msg)
      node.sendToNode(msg)
    }
  }
  
  fun nonLobbyPingMsg(
    stateHolder: StateHolder,
    nodesHolder: ClusterNodesHandler,
    localNode: LocalNode,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    val (ms, _) = stateHolder.masterDeputy ?: return
    if(ms.first != ipAddress) return
    nodesHolder[ipAddress]?.let {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, ms.second, localNode.nodeId
      )
      it.sendToNode(ack)
    }
  }
  
  fun nonLobbyOnAck(
    nodesHolder: ClusterNodesHandler,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    nodesHolder[ipAddress]?.ackMessage(message)
  }
  
  /**
   * @return `masterInfo` `Pair<InetSocketAddress, Int>?` if message
   * submitted, else `null`
   * */
  fun onStateMsg(
    stateHolder: StateHolder,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage
  ): Pair<InetSocketAddress, Int>? {
    val (ms, _) = stateHolder.masterDeputy ?: return null
    if(ms.first != ipAddress) return null
    val curState = stateHolder.gameState ?: return null
    val newStateSeq = message.state.state.stateOrder
    if(curState.stateOrder >= newStateSeq) return ms
    runBlocking {
      stateHolder.handleContextEvent(
        ContextEvent.Internal.NewState(message.state.state)
      )
    }
    return ms
  }
  
  suspend fun onJoinGameAck(
    stateMachine: NetworkStateHolder, event: Event.State.ByInternal.JoinReqAck
  ) {
    Logger.trace { "join to game with $event" }
    when(event.onEventAck.playerRole) {
      NodeRole.VIEWER -> {
        joinAsViewer(event)
      }
      
      NodeRole.NORMAL -> {
        joinAsActive(event)
      }
      
      else            -> {
        Logger.error { "incorrect $event" }
        throw IllegalChangeStateAttempt("incorrect $event")
      }
    }
  }
  
  fun atFromMasterNodeDeputyNow(
    master: Pair<InetSocketAddress, Int>,
    deputy: Pair<InetSocketAddress, Int>?,
    localNode: LocalNode,
    stateHolder: StateHolder,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(master.second != message.senderId || ipAddress != master.first) return false
    if(message.receiverId != localNode.nodeId) return false
    if(deputy?.second == localNode.nodeId) return true
    runBlocking {
      stateHolder.handleContextEvent(ContextEvent.Internal.DeputyNow(localNode))
    }
    return true
  }
  
  fun atFromMasterPlayerDead(
    master: Pair<InetSocketAddress, Int>,
    localNode: LocalNode,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(message.receiverId != localNode.nodeId) return false
    if(master.second != message.senderId || ipAddress != master.first) return false
    localNode.detach()
    return true
  }
  
  fun atFromMasterNodeMasterNow(
    master: Pair<InetSocketAddress, Int>,
    deputy: Pair<InetSocketAddress, Int>?,
    localNode: LocalNode,
    nodesHolder: ClusterNodesHandler,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(master.second != message.senderId || ipAddress != master.first) return false
    if(deputy?.second != message.receiverId) return false
    if(deputy.second != localNode.nodeId) return false
    nodesHolder[ipAddress]?.detach()
    return true
  }
  
  fun atFromDeputyDeputyMasterNow(
    master: Pair<InetSocketAddress, Int>,
    deputy: Pair<InetSocketAddress, Int>?,
    nodesHolder: ClusterNodesHandler,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(message.senderId != deputy?.second || ipAddress != deputy.first) return false
    nodesHolder[master.first]?.detach()
    return true
  }
  
  
  private fun correctMasterInfo(
    message: SnakesProto.GameMessage,
    ms: Pair<InetSocketAddress, Int>,
    ipAddress: InetSocketAddress
  ): Boolean {
    return ms.second == message.senderId && ipAddress == ms.first
  }
  
  fun findDeputyInState(
    msId: Int, state: SnakesProto.GameState
  ): SnakesProto.GamePlayer? {
    return state.players.playersList.firstOrNull {
      it.id != msId && it.ipAddress.isNotBlank() && it.port in PortValuesRange && it.role == SnakesProto.NodeRole.NORMAL
    }
  }
}
