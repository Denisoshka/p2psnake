package d.zhdanov.ccfit.nsu.core.network.node.connected

import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.NetworkGameContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.Logger
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.RetryJoinLater
import d.zhdanov.ccfit.nsu.core.network.states.abstr.ConnectedActor
import d.zhdanov.ccfit.nsu.core.network.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress

class MasterState(
  val stateHolder: StateHolder,
  val localNode: LocalNode,
  val gameEngine: NetworkGameContext,
  val gameConfig: InternalGameConfig,
) : NodeState.MasterStateT, ConnectedActor {
  private val gameController: GameController = stateHolder.gameController
  val nodesHolder: ClusterNodesHolder = stateHolder.nodesHolder
  
  init {
    Logger.info { "$this init" }
    gameEngine.launch()
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    val joinMsg = message.join
    try {
      MessageUtils.Preconditions.checkJoin(joinMsg)
    } catch(e: Exception) {
      val err = MessageUtils.MessageProducer.getErrorMsg(
        stateHolder.nextSeqNum, e.message ?: ""
      )
      stateHolder.sendUnicast(err, ipAddress)
      Logger.error(e) { "during joinHandle" }
      return
    }
    
    try {
      when(joinMsg.requestedRole) {
        SnakesProto.NodeRole.NORMAL -> {
          handleActiveJoin(ipAddress, message)
        }
        
        else                        -> {
          handlePassiveJoin(ipAddress, message)
        }
      }
    } catch(e: Exception) {
      Logger.error(e) { "during node registration" }
    }
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    nodesHolder[ipAddress]?.let {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, localNode.nodeId, localNode.nodeId
      )
      it.sendToNode(ack)
    }
  }
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    Utils.nonLobbyOnAck(
      nodesHolder = nodesHolder, ipAddress = ipAddress, message = message
    )
  }
  
  override fun submitSteerMsg(steerMsg: SnakesProto.GameMessage.SteerMsg) {
    localNode.payload.handleEvent(steerMsg, 0)
  }
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    if(!MessageUtils.RoleChangeIdentifier.fromNodeNodeLeave(message)) return
    nodesHolder[ipAddress]?.apply {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, nodeId, this.nodeId
      )
      sendToNode(ack)
      detach()
    }
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    nodesHolder[ipAddress]?.let {
      if(it.payload.handleEvent(message.steer, message.msgSeq, null)) {
        val ack = MessageUtils.MessageProducer.getAckMsg(
          message.msgSeq, localNode.nodeId, it.nodeId
        )
        it.sendToNode(ack)
      }
    }
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  ) {
    /**not handle*/
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  
  override fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ): NodeState {
    gameEngine.shutdown()
    nodesHolder.shutdown()
    gameController.openLobby()
    return LobbyState(
      stateHolder
    )
  }
  
  override fun toPassive(
    changeAccessToken: Any
  ): NodeState {
    val (_, depInfo) = stateHolder.masterDeputy!!
    gameEngine.shutdown()
    if(depInfo != null) {
      val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
        stateHolder.nextSeqNum,
        senderId = localNode.nodeId,
        receiverId = depInfo.second,
        senderRole = NodeRole.VIEWER,
        receiverRole = NodeRole.MASTER,
      )
      nodesHolder[depInfo.first]?.let {
        it.sendToNode(msg)
        it.addMessageForAck(msg)
      }
      return PassiveState(
        stateHolder = stateHolder,
        localNode = localNode,
        gameConfig = gameConfig,
      ).apply {
        nodesHolder.filter {
          it.value.nodeId != depInfo.second || it.value.nodeId != localNode.nodeId
        }.forEach { it.value.shutdown() }
        localNode.detach()
      }
    } else {
      return toLobby(Event.State.ByController.SwitchToLobby, changeAccessToken)
    }
  }
  
  override fun atNodeDetachPostProcess(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    accessToken: Any
  ): NodeState? {
    if(node.nodeId == localNode.nodeId) {
      /**
       * Ну вообще у нас может отвалиться либо наша нода, когда мы становимся
       * вивером, тогда мы должны просто сказать что мы отваливаемся,
       * после перейдем в пассивный режим и будем ждать пока нам начнет
       * присылать сообщения депути(если и он отъебнул в этот момент то нас
       * это не воднует, в протоколе не описани что делать), если он не
       * пришлет нам нихуя, то мы из пассив уйдем в лобби
       **/
      if(dpInfo != null) {
        nodesHolder[dpInfo.first]?.let {
          val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
            msgSeq = stateHolder.nextSeqNum,
            senderId = localNode.nodeId,
            receiverId = dpInfo.second,
            senderRole = NodeRole.VIEWER,
            receiverRole = NodeRole.MASTER,
          )
          it.sendToNode(msg)
          it.addMessageForAck(msg)
        }
        return toPassive(accessToken)
      } else {
        return toLobby(Event.State.ByController.SwitchToLobby, accessToken)
      }
    } else if(node.nodeId != localNode.nodeId) {
      /**
       * Либо отъебнули не мы и тогда все ок, просто говорим что чел умер
       **/
      val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
        msgSeq = stateHolder.nextSeqNum,
        senderId = localNode.nodeId,
        receiverId = node.nodeId,
        senderRole = NodeRole.MASTER,
        receiverRole = NodeRole.VIEWER
      )
      node.sendToNode(msg)
      node.addMessageForAck(msg)
    }
    return null
  }
  
  private fun handleActiveJoin(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    ClusterNode(
      nodeState = Node.NodeState.Passive,
      nodeId = stateHolder.nextNodeId,
      ipAddress = ipAddress,
      clusterNodesHolder = nodesHolder,
      name = message.join.playerName,
    ).apply {
      nodesHolder.registerNode(this)
      if(!gameEngine.offerPlayer(this to message)) {
        val err = MessageUtils.MessageProducer.getErrorMsg(
          message.msgSeq, RetryJoinLater
        )
        sendToNode(err)
        shutdown()
      }
    }
  }
  
  private fun handlePassiveJoin(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    nodesHolder[ipAddress]?.let {
      if it.nodeState
      MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, it.nodeId, localNode.nodeId
      )
      return
    }
    ClusterNode(
      nodeState = Node.NodeState.Passive,
      nodeId = stateHolder.nextNodeId,
      ipAddress = ipAddress,
      clusterNodesHolder = nodesHolder,
      name = message.join.playerName,
    ).apply {
      nodesHolder.registerNode(this)
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, localNode.nodeId, this.nodeId
      )
      sendToNode(ack)
      addMessageForAck(ack)
    }
  }
}