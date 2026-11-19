package kinoko.bot;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import kinoko.server.packet.OutPacket;
import java.util.function.Consumer;

/** Native server transport for a dedicated character. Outbound packets are consumed, never queued indefinitely. */
public final class CrewChannel extends EmbeddedChannel {
    public CrewChannel(Consumer<OutPacket> observer) {
        super(new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                try {
                    if (message instanceof OutPacket packet && observer != null) observer.accept(packet);
                    promise.setSuccess();
                } catch (Exception e) { promise.setFailure(e); }
            }
        });
    }
}
