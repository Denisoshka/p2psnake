package d.zhdanov.ccfit.nsu.core.network.nethandlers.inboundhandlers

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket

class UnicastHandler() : SimpleChannelInboundHandler<DatagramPacket>() {
  override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
    TODO(
      "мб нужно сделать здесь просто десириализацию и отправлять только " +
          "прото сообщение а в контексте оно уже толкьо косле того как " +
          "узнает какого оно типа десириализуется"
    )
  }
}