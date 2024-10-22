package core.network.nethandlers

import d.zhdanov.ccfit.nsu.core.network.nethandlers.inboundhandlers.UnicastHandler
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import java.net.InetSocketAddress


class UnicastNetHandler<MessageT, MessageDescriptor>(
  private val msgUtils: MessageUtilsT<MessageT, MessageDescriptor>
) {
  private val group = NioEventLoopGroup()
  private lateinit var bootstrap: Bootstrap
  private lateinit var channel: DatagramChannel

  init {
    launch()
  }

  private fun launch() {
    bootstrap = Bootstrap().apply {
      group(group)
      channel(NioDatagramChannel::class.java)
      option(ChannelOption.SO_BROADCAST, true)
      handler(object : ChannelInitializer<DatagramChannel>() {
        override fun initChannel(ch: DatagramChannel) {
          ch.pipeline().addLast(UnicastHandler())
        }
      })
    }
  }

  fun start() {
    channel = bootstrap.bind(0).sync().channel() as DatagramChannel
    channel.joinGroup(InetSocketAddress(TODO(), TODO())).sync()
  }

  fun sendMessage(message: MessageT, address: InetSocketAddress) {
    val data = Unpooled.wrappedBuffer(msgUtils.toBytes(message))
    val packet = DatagramPacket(data, address)
    channel.writeAndFlush(packet)
    TODO()
  }
}