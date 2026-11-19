package kinoko.bot;

import kinoko.provider.*;
import kinoko.server.node.Client;
import kinoko.world.item.*;
import kinoko.world.user.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class KpqCrewPreparationTest {
    @BeforeAll static void providers() { ItemProvider.initialize();SkillProvider.initialize();StringProvider.initialize();MapProvider.initialize(); }

    @Test void allRolesHaveEarnedAPAndSPAndLegalOrdinaryGear() {
        for(var role:KpqCrewPreparation.Role.values()) {
            var data=KpqCrewPreparation.create(1,100+role.ordinal(),"CrewTest"+role.ordinal(),role);
            var packets=new ArrayList<kinoko.server.packet.OutPacket>();
            var channel=new CrewChannel(packets::add);
            var client=new Client(null,channel,true);
            var user=new User(client,data);client.setUser(user);
            var field=new kinoko.world.field.Field(null,MapProvider.getMapInfo(910340700).orElseThrow());
            user.setField(field);
            KpqCrewPreparation.equip(user,role);
            assertEquals(25,user.getLevel());
            assertEquals(role.job.getJobId(),user.getJob());
            var cs=user.getCharacterStat();
            assertEquals(145,cs.getBaseStr()+cs.getBaseDex()+cs.getBaseInt()+cs.getBaseLuk()+cs.getAp());
            assertEquals(0,cs.getAp());
            int spent=data.getSkillManager().getSkillRecords().stream().mapToInt(record -> record.getSkillLevel()).sum();
            assertEquals(1+3*(25-role.advanceLevel),spent+cs.getSp().getNonExtendSp());
            for(var record:data.getSkillManager().getSkillRecords()) {
                var info=SkillProvider.getSkillInfoById(record.getSkillId()).orElseThrow();
                assertTrue(record.getSkillLevel()<=info.getMaxLevel());
                for(var requirement:info.getReqSkills().entrySet()) assertTrue(user.getSkillLevel(requirement.getKey())>=requirement.getValue());
            }
            assertEquals(200,user.getInventoryManager().getItemCount(KpqCrewPreparation.GINGER_ALE));
            if(role==KpqCrewPreparation.Role.ARCHER) assertEquals(2000,user.getInventoryManager().getItemCount(KpqCrewPreparation.BOW_ARROW));
            for(var item:user.getInventoryManager().getEquipped().getItems().values()) {
                var info=ItemProvider.getItemInfo(item.getItemId()).orElseThrow();
                assertFalse(info.isCash());assertTrue(info.getReqLevel()<=25);
                String name=StringProvider.getItemName(item.getItemId()).toLowerCase();
                assertFalse(name.contains("astaroth")||name.contains("zakum")||name.contains("snowboard"));
                assertEquals(0,item.getEquipData().getCuc());
            }
            Item weapon=user.getInventoryManager().getEquipped().getItem(BodyPart.WEAPON.getValue());
            assertEquals(role.weapon,WeaponType.getByItemId(weapon.getItemId()));
            int hp=user.getHp();
            KpqCrewPreparation.restock(user,role);
            assertEquals(200,user.getInventoryManager().getItemCount(KpqCrewPreparation.GINGER_ALE));
            assertEquals(hp,user.getHp());
            assertNull(channel.readOutbound(),"Consumed packets must not accumulate in embedded transports");
            channel.finishAndReleaseAll();field.getFieldEventFuture().cancel(true);
        }
    }

    @Test void ordinaryNativeClientsRemainDistinctFromCrewClients() {
        var normal=new Client(null,new CrewChannel(null));
        var bot=new Client(null,new CrewChannel(null),true);
        assertFalse(normal.isAutomated());assertTrue(bot.isAutomated());
        normal.getChannel().close();bot.getChannel().close();
    }
    @Test void onlyDedicatedCrewAccountNamesCanBeReused() {
        assertTrue(KpqCrewService.ownsCrewAccount("otono_kpq_2_sword",KpqCrewPreparation.Role.SWORD));
        assertTrue(KpqCrewService.ownsCrewAccount("otono_kpq_sword_1",KpqCrewPreparation.Role.SWORD));
        assertFalse(KpqCrewService.ownsCrewAccount("player",KpqCrewPreparation.Role.SWORD));
        assertFalse(KpqCrewService.ownsCrewAccount("otono_kpq_integration_owner",KpqCrewPreparation.Role.SWORD));
        assertFalse(KpqCrewService.ownsCrewAccount("otono_kpq_2_mage",KpqCrewPreparation.Role.SWORD));
    }}
