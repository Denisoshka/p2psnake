package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.game.impl.ClusterNodesHandler
import core.network.core.connection.lobby.impl.NetNode
import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.ActiveStateUtils
import core.network.core.states.utils.MasterStateUtils
import core.network.core.states.utils.PassiveStateUtils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.LobbyStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(LobbyState::class.java.name)

class LobbyState(
  private val stateHolder: NetworkStateHolder,
  private val gameController: GameController,
  private val netNodesHandler: NetNodeHandler,
) : LobbyStateT {
  override fun requestJoinToGame(
    event: Event.State.ByController.JoinReq
  ) {
    val addr = InetSocketAddress(
      event.gameAnnouncement.host, event.gameAnnouncement.port
    )
    
    val node = netNodesHandler[addr] ?: run {
      val resendDelay = getResendDelay(
        event.gameAnnouncement.announcement.config.stateDelayMs
      )
      val thresholdDelay = getThresholdDelay(
        event.gameAnnouncement.announcement.config.stateDelayMs
      )
      val newNode = NetNode(
        context = netNodesHandler,
        ipAddress = addr,
        resendDelay = resendDelay.toLong(),
        thresholdDelay = thresholdDelay.toLong(),
      )
      netNodesHandler.registerNode(newNode)
    }
    val msg = MessageUtils.MessageProducer.getJoinMsg(
      msgSeq = stateHolder.nextSeqNum,
      playerType = SnakesProto.PlayerType.HUMAN,
      playerName = event.playerName,
      gameName = event.gameAnnouncement.announcement.gameName,
      nodeRole = MessageUtils.MessageProducer.nodeRoleToProto(
        event.playerRole
      )
    )
    node.sendToNode(msg)
    node.addMessageForAck(msg)
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateHolder.onPingMsg(ipAddress, message, me)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val node = netNodesHandler[ipAddress] ?: return
    val onAckMsg = node.ackMessage(message) ?: return
    if(onAckMsg.req.typeCase == SnakesProto.GameMessage.TypeCase.JOIN) {
      Logger.trace {
        "receive join ack from $ipAddress with id ${message.receiverId}"
      }
      
    }
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val node = netNodesHandler[ipAddress] ?: return
    val onAckMsg = node.ackMessage(message) ?: return
    if(onAckMsg.req.typeCase == SnakesProto.GameMessage.TypeCase.JOIN) {
      Logger.trace {
        "receive join error from $ipAddress with cause ${message.error.errorMessage}"
      }
    }
  }
  
  override fun cleanup() {
    netNodesHandler.shutdown()
  }
  
  override fun toMaster(
    changeAccessToken: Any,
    nodesHandler: ClusterNodesHandler,
    event: Event.State.ByController.LaunchGame
  ) {
    try {
      val playerInfo = GamePlayerInfo(event.internalGameConfig.playerName, 0)
      nodesHandler.launch()
      MasterStateUtils.prepareMasterContext(
        clusterNodesHandler = nodesHandler,
        gamePlayerInfo = playerInfo,
        gameConfig = event.internalGameConfig,
        stateHolder = stateHolder,
      ).apply {
        stateHolder.setupNewState(this, changeAccessToken)
      }
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error during launch game" }
      throw e
    }
    gameController.openGameScreen(event.internalGameConfig)
  }
  
  override fun toActive(
    nodesHandler: ClusterNodesHandler,
    event: Event.State.ByInternal.JoinReqAck,
    changeAccessToken: Any
  ) {
    try {
      val destAddr = InetSocketAddress(
        event.onEventAck.gameAnnouncement.host,
        event.onEventAck.gameAnnouncement.port
      )
      nodesHandler.launch()
      ActiveStateUtils.prepareActiveState(
        clusterNodesHandler = nodesHandler,
        stateHolder = stateHolder,
        destAddr = destAddr,
        internalGameConfig = event.internalGameConfig,
        masterId = event.senderId,
        playerId = event.gamePlayerInfo.playerId
      ).apply { stateHolder.setupNewState(this, changeAccessToken) }
    } catch(e: Exception) {
      nodesHandler.shutdown()
      Logger.trace { "joined as ${SnakesProto.NodeRole.NORMAL} to $event" }
      throw e
    }
    gameController.openGameScreen(event.internalGameConfig)
  }
  
  
  override fun toPassive(
    clusterNodesHandler: ClusterNodesHandler,
    event: Event.State.ByInternal.JoinReqAck,
    changeAccessToken: Any
  ) {
    try {
      val destAddr = InetSocketAddress(
        event.onEventAck.gameAnnouncement.host,
        event.onEventAck.gameAnnouncement.port
      )
      clusterNodesHandler.launch()
      PassiveStateUtils.createPassiveState(
        stateHolder = stateHolder,
        clusterNodesHandler = clusterNodesHandler,
        destAddr = destAddr,
        internalGameConfig = event.internalGameConfig,
        masterId = event.senderId,
        playerId = event.gamePlayerInfo.playerId,
      ).apply { stateHolder.setupNewState(this, changeAccessToken) }
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error during switch to passive game" }
      clusterNodesHandler.shutdown()
      throw e
    }
  }
  
  companion object LobbyStateDelayProvider {
    private const val MAX_THRESHOLD_COEF = 3.0
    private const val MAX_RESEND_DELAY_COEF = 0.1
    
    fun getResendDelay(stateDelay: Int): Double {
      return stateDelay * MAX_RESEND_DELAY_COEF
    }
    
    fun getThresholdDelay(stateDelay: Int): Double {
      return stateDelay * MAX_THRESHOLD_COEF
    }
  }
}