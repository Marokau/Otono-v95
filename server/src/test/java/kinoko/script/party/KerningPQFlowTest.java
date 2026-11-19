package kinoko.script.party;

import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import kinoko.provider.*;
import kinoko.script.common.ScriptManager;
import kinoko.script.common.ScriptManagerImpl;
import kinoko.server.field.Instance;
import kinoko.server.field.InstanceFieldStorage;
import kinoko.server.node.Client;
import kinoko.server.packet.NioBufferInPacket;
import kinoko.server.packet.OutPacket;
import kinoko.util.Rect;
import kinoko.world.field.Field;
import kinoko.world.field.drop.Drop;
import kinoko.world.field.drop.DropOwnType;
import kinoko.world.field.drop.DropEnterType;
import kinoko.world.item.*;
import kinoko.world.quest.QuestManager;
import kinoko.world.skill.SkillManager;
import kinoko.world.user.*;
import kinoko.world.user.data.CoupleRecord;
import kinoko.world.user.stat.CharacterStat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Native script/asset integration fixture; positions and damage are test inputs, not a bot gameplay run. */
class KerningPQFlowTest {
    @BeforeAll static void providers() {
        ItemProvider.initialize(); SkillProvider.initialize(); MapProvider.initialize(); MobProvider.initialize();
        NpcProvider.initialize(); ReactorProvider.initialize(); QuestProvider.initialize(); EtcProvider.initialize();
        RewardProvider.initialize();
    }

    static final class Participant {
        final List<OutPacket> packets = new ArrayList<>();
        final List<String> dialogue = new ArrayList<>();
        final User user;
        final ScriptManager sm;
        final Instance instance;
        final InstanceFieldStorage storage;
        final Field lobby;
        final List<Field> extraFields = new ArrayList<>();
        int menu;
        boolean yes = true;

        Participant(int id, boolean leader, Instance instance, InstanceFieldStorage storage, Field lobby) {
            this.instance=instance; this.storage=storage; this.lobby=lobby;
            SocketChannel socket = (SocketChannel) Proxy.newProxyInstance(SocketChannel.class.getClassLoader(),
                    new Class[]{SocketChannel.class}, (proxy, method, args) -> {
                        if (method.getName().equals("writeAndFlush")) {
                            packets.add((OutPacket) args[0]);
                            return new DefaultChannelPromise((Channel) proxy, ImmediateEventExecutor.INSTANCE).setSuccess();
                        }
                        throw new AssertionError("Unexpected socket method " + method.getName());
                    });
            var data = new CharacterData(id);
            var stat = new CharacterStat(); stat.setId(id); stat.setName("KpqTest"+id);
            stat.setLevel((short)20); stat.setHp(500); stat.setMaxHp(500); stat.setMp(500); stat.setMaxMp(500);
            data.setCharacterStat(stat); data.setItemSnCounter(new AtomicInteger(1));
            var inventory = new InventoryManager();
            inventory.setEquipped(new Inventory(100)); inventory.setEquipInventory(new Inventory(24));
            inventory.setConsumeInventory(new Inventory(24)); inventory.setInstallInventory(new Inventory(24));
            inventory.setEtcInventory(new Inventory(24)); inventory.setCashInventory(new Inventory(24)); inventory.setMoney(100);
            data.setCoupleRecord(new CoupleRecord());
            data.setInventoryManager(inventory); data.setSkillManager(new SkillManager()); data.setQuestManager(new QuestManager());
            user = new User(new Client(null,socket),data); user.setPartyInfo(new PartyInfo(7,id-1,leader));
            user.setField(lobby); lobby.getUserPool().addUser(user);
            sm = (ScriptManager) Proxy.newProxyInstance(ScriptManager.class.getClassLoader(), new Class[]{ScriptManager.class}, (proxy,method,args) -> {
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy,method,args);
                switch (method.getName()) {
                    case "sayOk", "sayNext", "sayBoth" -> { dialogue.add((String)args[0]); return null; }
                    case "askMenu" -> { return menu; }
                    case "askYesNo" -> { return yes; }
                    case "partyWarpInstance" -> {
                        // Entry creates the already-resolved native instance fixture, then installs its rules.
                        @SuppressWarnings("unchecked") var variables=(Map<String,String>)args[4];
                        variables.forEach(instance::setVariable);
                        for (User member : List.copyOf(user.getField().getUserPool().getPartyMembers(7))) move(member,storage.getFieldById(KerningPQ.STAGE_1).orElseThrow());
                        return null;
                    }
                    case "warp" -> {
                        int idTarget=(int)args[0];
                        Field target=idTarget==KerningPQ.LOBBY ? lobby : storage.getFieldById(idTarget).orElseGet(() -> {
                            Field created = new Field(null,MapProvider.getMapInfo(idTarget).orElseThrow());
                            extraFields.add(created); return created;
                        });
                        move(user,target); return null;
                    }
                }
                try { return method.invoke(new ScriptManagerImpl(user,user.getField(),user,"party1_play",9020001),args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        }
        void move(User member, Field target) {
            Field source=member.getField();
            if (source.getFieldStorage() == storage && target.getFieldStorage()!=storage) instance.clearRestrictedItems(member);
            boolean leavingInstance = source.getFieldStorage()!=target.getFieldStorage();
            source.removeUser(member,leavingInstance);
            if(source.getFieldStorage()==storage && !leavingInstance) assertTrue(instance.getUsers().contains(member), "Stage transfers must preserve instance membership");
            member.setField(target); member.setX(0); member.setY(0);
            target.getUserPool().addUser(member);
            if (target.getFieldStorage()==storage) instance.addUser(member);
        }
        void stand(Rect area) { user.setX((area.getLeft()+area.getRight())/2); user.setY((area.getTop()+area.getBottom())/2); }
    }

    @Test void fourPersonPartyCanContinueWithThreeRemainingMembers() {
        var instance=new Instance(19002,KerningPQ.EXIT,null,Instant.now().plusSeconds(1800));
        var ids=List.of(KerningPQ.STAGE_1,KerningPQ.STAGE_2,KerningPQ.STAGE_3,KerningPQ.STAGE_4,KerningPQ.STAGE_5);
        var storage=InstanceFieldStorage.from(instance,ids.stream().map(id -> MapProvider.getMapInfo(id).orElseThrow()).toList());
        instance.setFieldStorage(storage);
        var lobby=new Field(null,MapProvider.getMapInfo(KerningPQ.LOBBY).orElseThrow());
        var participants=new ArrayList<Participant>();
        try {
            for(int id=1;id<=4;id++) participants.add(new Participant(id,id==1,instance,storage,lobby));
            var leader=participants.getFirst();
            KerningPQ.party1_enter(leader.sm);
            assertEquals("3",instance.getVariable("required_passes").orElseThrow());
            participants.get(3).move(participants.get(3).user,lobby);
            instance.setVariable("ready_after","0");
            instance.update(Instant.now().plusSeconds(2));
            assertEquals(3,instance.getUsers().size());
            assertEquals("2",instance.getVariable("required_passes").orElseThrow());
            assertFalse(instance.getVariable("failed").orElse("").equals("1"));
        } finally { storage.clear(); lobby.getFieldEventFuture().cancel(true); }
    }

    @Test void threeParticipantsCanProgressThroughEveryStageAndClaimOneRewardEach() {
        var instance = new Instance(19001,KerningPQ.EXIT,null,Instant.now().plusSeconds(1800));
        var ids=List.of(KerningPQ.STAGE_1,KerningPQ.STAGE_2,KerningPQ.STAGE_3,KerningPQ.STAGE_4,KerningPQ.STAGE_5);
        var storage=InstanceFieldStorage.from(instance,ids.stream().map(id -> MapProvider.getMapInfo(id).orElseThrow()).toList());
        instance.setFieldStorage(storage);
        var lobby=new Field(null,MapProvider.getMapInfo(KerningPQ.LOBBY).orElseThrow());
        var participants=new ArrayList<Participant>();
        try {
            for (int id=1;id<=3;id++) participants.add(new Participant(id,id==1,instance,storage,lobby));
            var leader=participants.getFirst();
            KerningPQ.party1_enter(leader.sm);
            assertEquals(3,instance.getUsers().size());
            assertEquals("2",instance.getVariable("required_passes").orElseThrow());
            KerningPQ.party1_play(leader.sm);
            for (var member:participants.subList(1,3)) {
                KerningPQ.party1_play(member.sm);
                int count=Integer.parseInt(instance.getVariable("coupon_mission_"+member.user.getCharacterId()).orElseThrow());
                assertTrue(member.sm.addItem(KerningPQ.COUPON,count)); // fixture input: collected coupons
                KerningPQ.party1_play(member.sm);
                assertEquals(1,member.sm.getItemCount(KerningPQ.PASS));
                assertTrue(member.sm.removeItem(KerningPQ.PASS,1)); // fixture input: physical pass handed to leader
                assertTrue(leader.sm.addItem(KerningPQ.PASS,1));
            }
            KerningPQ.party1_play(leader.sm);
            assertEquals("1",instance.getVariable("stage1_gate").orElseThrow());
            for (var member:participants) KerningPQ.party_portal(member.sm);
            assertEquals(KerningPQ.STAGE_2,leader.user.getFieldId());
            for (int stage:List.of(2,3)) {
                KerningPQ.party1_play(leader.sm);
                String answer=instance.getVariable("stage"+stage+"_answer").orElseThrow();
                int player=0;
                for(int i=0;i<answer.length();i++) if(answer.charAt(i)=='1') participants.get(player++).stand(leader.user.getField().getMapInfo().getAreas().get(i));
                if (stage==3) {
                    // A three-person party can keep the leader beside Cloto by using Nella's native puppet item.
                    leader.user.setX(502); leader.user.setY(25);
                    int beforeMoney=leader.user.getInventoryManager().getMoney();
                    KerningPQ.party1_out(leader.sm); // menu option 0 buys a puppet
                    assertTrue(leader.user.getInventoryManager().getMoney()<beforeMoney);
                    assertTrue(leader.sm.removeItem(KerningPQ.PUPPET,1));
                    int correct=0;
                    for(int i=0;i<answer.length();i++) if(answer.charAt(i)=='1') {
                        Rect area=leader.user.getField().getMapInfo().getAreas().get(i);
                        if(correct<2) participants.get(correct+1).stand(area);
                        else {
                            var item=ItemProvider.getItemInfo(KerningPQ.PUPPET).orElseThrow().createItem(500,1);
                            var drop=Drop.item(DropOwnType.USEROWN,leader.user,item,leader.user.getCharacterId());
                            leader.user.getField().getDropPool().addDrop(drop,DropEnterType.CREATE,(area.getLeft()+area.getRight())/2,area.getTop(),0);
                            assertTrue(area.isInsideRect(drop.getX(),drop.getY()),"Puppet must land inside its platform area");
                            assertEquals(instance.getExpireTime(),drop.getExpireTime());
                        }
                        correct++;
                    }
                }
                KerningPQ.party1_play(leader.sm);
                assertEquals("1",instance.getVariable("stage"+stage+"_gate").orElseThrow());
                for(var member:participants) KerningPQ.party_portal(member.sm);
            }
            assertEquals(KerningPQ.STAGE_4,leader.user.getFieldId());
            KerningPQ.StageMsg_together(leader.sm);
            Field curseRoom=leader.user.getField();
            var eyes=new ArrayList<kinoko.world.field.mob.Mob>(); curseRoom.getMobPool().forEach(eyes::add);
            assertEquals(13,eyes.stream().filter(mob->mob.getTemplateId()==9300002).count());
            // Native mob damage/death/drop handling; damage amount is deliberately supplied by this test fixture.
            for(var mob:eyes) mob.damage(leader.user,mob.getHp(),0);
            assertEquals("1",instance.getVariable("stage4_gate").orElseThrow());
            for(var member:participants) KerningPQ.party_portal(member.sm);
            KerningPQ.StageMsg_together(leader.sm);
            var king=leader.user.getField().getMobPool().getByTemplateId(9300003).orElseThrow();
            king.damage(leader.user,king.getHp(),0);
            assertEquals("1",instance.getVariable("boss_defeated").orElseThrow());
            assertTrue(instance.getRewards().isPresent());
            for(int i=0;i<3;i++) {
                KerningPQRewards.select(participants.get(i).user,new NioBufferInPacket(new byte[]{(byte)i}));
                if(i==0) {
                    KerningPQRewards.claim(leader.user,new NioBufferInPacket(new byte[0]));
                    assertFalse(instance.getRewards().orElseThrow().hasClaimed(leader.user.getCharacterId()), "Prizes must stay hidden until everyone present has selected");
                }
            }
            for(int i=0;i<3;i++) {
                var member=participants.get(i);
                var prize=instance.getRewards().orElseThrow().selectedPrize(member.user.getCharacterId()).orElseThrow();
                int before=member.sm.getItemCount(prize.itemId());
                KerningPQRewards.claim(member.user,new NioBufferInPacket(new byte[0]));
                assertTrue(instance.getRewards().orElseThrow().hasClaimed(member.user.getCharacterId()));
                assertEquals(before+prize.quantity(),member.sm.getItemCount(prize.itemId()));
                KerningPQRewards.claim(member.user,new NioBufferInPacket(new byte[0]));
                assertEquals(before+prize.quantity(),member.sm.getItemCount(prize.itemId()));
            }
            for(var member:participants) {
                KerningPQ.party1_play(member.sm);
                assertEquals(KerningPQ.EXIT,member.user.getFieldId());
                KerningPQ.party1_out(member.sm);
                assertEquals(KerningPQ.LOBBY,member.user.getFieldId());
                assertEquals(0,member.sm.getItemCount(KerningPQ.COUPON));
                assertEquals(0,member.sm.getItemCount(KerningPQ.PASS));
                assertEquals(0,member.sm.getItemCount(KerningPQ.PUPPET));
            }
        } finally {
            for(var member:participants) member.extraFields.forEach(field -> field.getFieldEventFuture().cancel(true));
            storage.clear(); lobby.getFieldEventFuture().cancel(true);
        }
    }
}
