package kinoko.bot;

import kinoko.database.DatabaseManager;
import kinoko.packet.user.UserPacket;
import kinoko.packet.user.ChatType;
import kinoko.packet.world.WvsContext;
import kinoko.script.party.LudibriumPQ;
import kinoko.server.header.OutHeader;
import kinoko.server.node.Client;
import kinoko.server.node.ServerExecutor;
import kinoko.server.packet.NioBufferInPacket;
import kinoko.server.packet.OutPacket;
import kinoko.server.party.PartyRequest;
import kinoko.server.party.PartyResultType;
import kinoko.world.user.Account;
import kinoko.world.user.CharacterData;
import kinoko.world.user.User;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Dedicated crews only; no ambient training, trading or autonomous world population. */
public final class LpqCrewService {
    private static final Map<Integer,Session> crews=new ConcurrentHashMap<>();
    private LpqCrewService() {}

    public static boolean hasCrew(User leader) { return crews.containsKey(leader.getCharacterId()); }
    public static boolean stageAutomationReady() { return true; }
    public static void prepareEntry(User leader) {
        Session session=crews.get(leader.getCharacterId());if(session==null)return;
        for(Bot bot:session.bots) {
            if(bot.user.getFieldId()!=LudibriumPQ.LOBBY || bot.user.getPartyId()!=leader.getPartyId())throw new IllegalStateException("Crew is not assembled in the lobby");
            LpqCrewPreparation.equip(bot.user,bot.role);
        }
    }    public static String status(User leader) { Session session=crews.get(leader.getCharacterId());return session==null?"No crew":session.run==null?"Assembling":session.run.status(); }
    public static List<User> members(User leader) {
        Session session=crews.get(leader.getCharacterId());
        return session==null?List.of():session.bots.stream().map(bot -> bot.user).toList();
    }

    public static String assemble(User leader) {
        if(leader.isAutomated() || leader.getFieldId()!=LudibriumPQ.LOBBY || leader.getHp()<=0) return "Please assemble your crew in the LPQ lobby while alive.";
        if(hasCrew(leader)) return "Your crew is already assembled. Reuse these members rather than creating another batch.";
        if(leader.hasParty()) return "Please leave your current party before assembling a dedicated crew. Existing parties are left alone.";
        Session session=new Session(leader);
        if(crews.putIfAbsent(leader.getCharacterId(),session)!=null) return "Your crew is already being assembled.";
        try {
            for(var role:KpqCrewPreparation.Role.values()) session.bots.add(spawn(leader,role));
            session.inviteNext();
            session.tick=ServerExecutor.scheduleAtFixedRate(leader.getField(),session::update,250,500,TimeUnit.MILLISECONDS);
            return "I am assembling your three level-35 party members. You will be party leader. They will wait here with you. " +
                    "Speak with the Red Sign to enter. We will explain each stage and wait for your checks.";
        } catch(Exception e) {
            dismiss(leader);
            throw new IllegalStateException("Could not prepare the dedicated LPQ crew",e);
        }
    }

    static boolean ownsCrewAccount(String username,KpqCrewPreparation.Role role) {
        String job=role.name().toLowerCase(Locale.ROOT);
        return username.matches("otono_lpq_\\d+_"+job) || username.matches("otono_lpq_"+job+"_\\d+");
    }
    private static synchronized Account findFreeAccount(User leader,KpqCrewPreparation.Role role) {
        // Reuse the earliest available dedicated character, including the original audit crew.
        // A player's similarly named character is never adopted or modified.
        for(int suffix=1;suffix<10000;suffix++) {
            String name="LPq"+role.label+suffix;
            var character=DatabaseManager.characterAccessor().getCharacterByName(name);
            if(character.isPresent()) {
                var result=DatabaseManager.accountAccessor().getAccountById(character.get().getAccountId());
                if(result.isEmpty() || !ownsCrewAccount(result.get().getUsername(),role) || leader.getConnectedServer().isConnected(result.get()))continue;
                return result.get();
            }
            String username="otono_lpq_"+role.name().toLowerCase(Locale.ROOT)+"_"+suffix;
            var result=DatabaseManager.accountAccessor().getAccountByUsername(username);
            if(result.isEmpty()) {
                if(!DatabaseManager.accountAccessor().newAccount(username,UUID.randomUUID().toString()))throw new IllegalStateException("Crew account creation failed");
                result=DatabaseManager.accountAccessor().getAccountByUsername(username);
            }
            if(result.isPresent() && !leader.getConnectedServer().isConnected(result.get()))return result.get();
        }
        throw new IllegalStateException("No available dedicated crew account");
    }
    private static Bot spawn(User leader,KpqCrewPreparation.Role role) {
        Account account=findFreeAccount(leader,role);
        if(leader.getConnectedServer().isConnected(account)) throw new IllegalStateException("This dedicated crew member is already connected");
        var avatars=DatabaseManager.characterAccessor().getAvatarDataByAccountId(account.getId());
        CharacterData data;
        if(!avatars.isEmpty()) {
            if(avatars.size()!=1) throw new IllegalStateException("Unexpected characters in dedicated crew account");
            data=DatabaseManager.characterAccessor().getCharacterById(avatars.getFirst().getCharacterId()).orElseThrow();
            if(data.getCharacterStat().getJob()!=LpqCrewPreparation.job(role).getJobId() && data.getCharacterStat().getJob()!=role.job.getJobId()) throw new IllegalStateException("Unexpected job on dedicated crew member");
        } else {
            String name=null;
            for(int suffix=1;suffix<10000;suffix++) {
                String candidate="LPq"+role.label+suffix;
                if(DatabaseManager.characterAccessor().checkCharacterNameAvailable(candidate)) { name=candidate;break; }
            }
            if(name==null) throw new IllegalStateException("No available crew name");
            int id=DatabaseManager.idAccessor().nextCharacterId().orElseThrow();
            data=LpqCrewPreparation.create(account.getId(),id,name,role);
            if(!DatabaseManager.characterAccessor().newCharacter(data)) throw new IllegalStateException("Crew character creation failed");
        }
        data.setPartyId(0);
        account.setChannelId(leader.getChannelId());
        var reference=new AtomicReference<Bot>();
        var channel=new CrewChannel(packet -> { Bot bot=reference.get(); if(bot!=null) bot.observe(packet); });
        var client=new Client(leader.getConnectedServer(),channel,true);
        client.setAccount(account);
        var user=new User(client,data);client.setUser(user);
        user.setField(leader.getField());
        var bot=new Bot(user,role,leader.getCharacterId());reference.set(bot);
        try {
            LpqCrewPreparation.equip(user,role);
            DatabaseManager.characterAccessor().saveCharacter(data);
            leader.getConnectedServer().addClient(client);
            leader.getConnectedServer().notifyUserConnect(user);
            user.warp(leader.getField(),leader.getField().getRandomStartPoint().orElseThrow(),true,false);
            return bot;
        } catch(Exception e) {
            client.close();
            throw e;
        }
    }

    public static void dismiss(User leader) {
        Session session=crews.remove(leader.getCharacterId());
        if(session==null) return;
        if(session.tick!=null) session.tick.cancel(false);
        boolean disband=leader.getPartyId()!=0 && (leader.getPartyId()==session.partyId || (session.partyId==0 && leader.isPartyBoss())) && leader.getClient().getUser()!=null && !leader.getConnectedServer().isShutdown();
        for(Bot bot:session.bots) ServerExecutor.submit(bot.user,() -> bot.leave(disband));
        if(disband) {
            leader.getConnectedServer().submitPartyRequest(leader,PartyRequest.withdrawParty());
        }
    }

    private static boolean allowedField(User user) {
        if(user.getFieldId()==LudibriumPQ.LOBBY || user.getFieldId()==LudibriumPQ.EXIT)return true;
        return user.getField().getFieldStorage() instanceof kinoko.server.field.InstanceFieldStorage storage && storage.getInstance().getVariable("lpq").orElse("").equals("1");
    }
    private static final class Session {
        final User leader;
        final List<Bot> bots=new ArrayList<>();
        final Instant deadline=Instant.now().plusSeconds(15);
        ScheduledFuture<?> tick;
        int partyId;
        int invited;
        LpqRun run;
        Session(User leader) { this.leader=leader; }
        void inviteNext() {
            Bot bot=bots.get(invited++);
            leader.getConnectedServer().submitPartyRequest(leader,PartyRequest.invite(bot.user.getCharacterName()));
        }
        void update() {
            if(leader.getClient().getUser()!=leader || !leader.getClient().getChannel().isActive() || !allowedField(leader) ||
                    bots.stream().anyMatch(bot -> !bot.user.getClient().getChannel().isActive())) {
                dismiss(leader);return;
            }
            if(partyId==0) {
                // Wait for each native join before issuing the next invite. Concurrent first invites can create separate parties.
                if(invited<bots.size() && leader.isPartyBoss() && leader.getPartyId()!=0 &&
                        bots.subList(0,invited).stream().allMatch(bot -> bot.user.getPartyId()==leader.getPartyId())) inviteNext();
                if(leader.isPartyBoss() && bots.stream().allMatch(bot -> bot.user.getPartyId()==leader.getPartyId())) {
                    partyId=leader.getPartyId();
                    run=new LpqRun(leader,bots.stream().map(bot -> new LpqRun.Member(bot.user,bot.role)).toList());
                    leader.write(WvsContext.scriptProgressMessage("Your LPQ crew is assembled. You are party leader."));
                    Bot spokesman=bots.getFirst();
                    spokesman.user.getField().broadcastPacket(UserPacket.userChat(spokesman.user,ChatType.NORMAL,
                            "We're ready. Speak with the Red Sign when you'd like to enter LPQ.",false));
                } else if(Instant.now().isAfter(deadline)) {
                    leader.write(WvsContext.scriptProgressMessage("The crew could not form its party. It has been dismissed; try assembling again."));
                    dismiss(leader);
                }
            } else if(leader.getPartyId()!=partyId || !leader.isPartyBoss()) dismiss(leader);
            else if(run!=null) ServerExecutor.submit(leader,run::update);
        }
    }

    private static final class Bot {
        final User user;
        final KpqCrewPreparation.Role role;
        final int ownerId;
        boolean closing;
        Bot(User user,KpqCrewPreparation.Role role,int ownerId) { this.user=user;this.role=role;this.ownerId=ownerId; }
        void observe(OutPacket outbound) {
            CrewActions.answer(user,outbound,0);
            var packet=new NioBufferInPacket(outbound.getData());
            if(packet.getRemaining()<3 || packet.decodeShort()!=OutHeader.PartyResult.getValue()) return;
            int result=Byte.toUnsignedInt(packet.decodeByte());
            if(result==PartyResultType.InviteParty.getValue() && !closing) {
                int inviter=packet.decodeInt();
                if(inviter==ownerId) ServerExecutor.submit(user,() -> user.getConnectedServer().submitPartyRequest(user,PartyRequest.joinParty(inviter)));
            } else if(result==PartyResultType.WithdrawParty_Done.getValue() && closing && !user.hasParty()) {
                ServerExecutor.submit(user,() -> user.getClient().close());
            }
        }
        void leave(boolean disbandRequested) {
            if(closing) return;
            closing=true;
            CrewMotion.cancel(user);
            if(user.hasParty() && !user.getConnectedServer().isShutdown()) {
                if(!disbandRequested) user.getConnectedServer().submitPartyRequest(user,PartyRequest.withdrawParty());
                ServerExecutor.schedule(user,() -> user.getClient().close(),2,TimeUnit.SECONDS);
            } else user.getClient().close();
        }
    }
}
