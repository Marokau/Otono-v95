package kinoko.server.node;

import io.netty.channel.Channel;
import kinoko.database.DatabaseManager;
import kinoko.packet.ClientPacket;
import kinoko.server.netty.NettyClient;
import kinoko.world.user.Account;
import kinoko.world.user.User;

public final class Client extends NettyClient {
    private boolean closed;
    private final boolean automated;
    private Account account;
    private User user;
    private byte[] machineId;
    private byte[] clientKey;

    public Client(ServerNode serverNode, Channel socketChannel) {
        this(serverNode, socketChannel, false);
    }

    public Client(ServerNode serverNode, Channel channel, boolean automated) {
        super(serverNode, channel);
        this.automated = automated;
    }

    public boolean isAutomated() { return automated; }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public byte[] getMachineId() {
        return machineId;
    }

    public void setMachineId(byte[] machineId) {
        this.machineId = machineId;
    }

    public byte[] getClientKey() {
        return clientKey;
    }

    public void setClientKey(byte[] clientKey) {
        this.clientKey = clientKey;
    }

    public void sendPing() {
        write(ClientPacket.aliveReq());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (user != null && !automated && !user.isInTransfer()) { kinoko.bot.KpqCrewService.dismiss(user); kinoko.bot.LpqCrewService.dismiss(user); }
        super.close();
        if (user == null) {
            if (account != null) {
                DatabaseManager.accountAccessor().saveAccount(account);
            }
        } else if (!user.isInTransfer()) {
            try {
                user.logout(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
            DatabaseManager.accountAccessor().saveAccount(account);
            DatabaseManager.characterAccessor().saveCharacter(user.getCharacterData());
        }
        getServerNode().removeClient(this);
        account = null;
        user = null;
    }
}
