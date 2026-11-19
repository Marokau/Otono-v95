package kinoko.server.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import kinoko.server.node.ServerNode;
import kinoko.server.packet.OutPacket;

public abstract class NettyClient {
    public static final AttributeKey<NettyClient> CLIENT_KEY = AttributeKey.valueOf("C");
    private final ServerNode serverNode;
    private final Channel socketChannel;

    public NettyClient(ServerNode serverNode, Channel socketChannel) {
        this.serverNode = serverNode;
        this.socketChannel = socketChannel;
    }

    public ServerNode getServerNode() {
        return serverNode;
    }

    public Channel getChannel() {
        return socketChannel;
    }

    public final void write(OutPacket outPacket) {
        socketChannel.writeAndFlush(outPacket);
    }

    public void close() {
        socketChannel.close();
    }
}
