package kinoko.bot;
import kinoko.provider.*;
import kinoko.server.node.Client;
import kinoko.world.field.Field;
import kinoko.world.item.*;
import kinoko.world.user.User;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class LpqCrewPreparationTest {
 @BeforeAll static void providers(){ItemProvider.initialize();SkillProvider.initialize();MapProvider.initialize();StringProvider.initialize();}
 @Test void secondJobCrewHasEarnedStatsOrdinaryGearAndFreshSupplies() throws Exception {
  for(var role:KpqCrewPreparation.Role.values()){
   var data=LpqCrewPreparation.create(1,700+role.ordinal(),"LpqPrep"+role.ordinal(),role);var channel=new CrewChannel(null);var node=new kinoko.server.node.ChannelServerNode(0,0);var central=kinoko.server.node.ChannelServerNode.class.getDeclaredField("centralClientFuture");central.setAccessible(true);central.set(node,new io.netty.channel.DefaultChannelPromise(channel));var client=new Client(node,channel,true);var user=new User(client,data);client.setUser(user);var field=new Field(null,MapProvider.getMapInfo(221023300).orElseThrow());user.setField(field);
   try{
    LpqCrewPreparation.equip(user,role);assertEquals(35,user.getLevel());assertEquals(LpqCrewPreparation.job(role).getJobId(),user.getJob());
    var cs=user.getCharacterStat();assertEquals(195,cs.getBaseStr()+cs.getBaseDex()+cs.getBaseInt()+cs.getBaseLuk()+cs.getAp());
    int spent=data.getSkillManager().getSkillRecords().stream().mapToInt(r->r.getSkillLevel()).sum();assertEquals(2+3*(35-role.advanceLevel),spent+cs.getSp().getNonExtendSp());
    assertEquals(200,user.getInventoryManager().getItemCount(KpqCrewPreparation.GINGER_ALE));assertEquals(100,user.getInventoryManager().getItemCount(LpqCrewPreparation.ALL_CURE));
    if(role==KpqCrewPreparation.Role.ARCHER)assertEquals(2000,user.getInventoryManager().getItemCount(KpqCrewPreparation.BOW_ARROW));
    for(var item:user.getInventoryManager().getEquipped().getItems().values()){var info=ItemProvider.getItemInfo(item.getItemId()).orElseThrow();assertTrue(info.getReqLevel()<=35);String name=StringProvider.getItemName(item.getItemId()).toLowerCase();assertFalse(name.contains("zakum")||name.contains("bandana"));}
    int before=spent;LpqCrewPreparation.equip(user,role);assertEquals(before,data.getSkillManager().getSkillRecords().stream().mapToInt(r->r.getSkillLevel()).sum(),"Re-entry must not fabricate SP");
   }finally{field.getFieldEventFuture().cancel(true);channel.finishAndReleaseAll();}
  }
 }
 @Test void onlyDedicatedLpqAccountsAreReusable(){assertTrue(LpqCrewService.ownsCrewAccount("otono_lpq_mage_1",KpqCrewPreparation.Role.MAGE));assertFalse(LpqCrewService.ownsCrewAccount("otono_kpq_mage_1",KpqCrewPreparation.Role.MAGE));assertFalse(LpqCrewService.ownsCrewAccount("player",KpqCrewPreparation.Role.SWORD));}
 @Test void equationUsesTheAnnouncedExpression(){assertEquals(468,LpqRun.solveEquation("400 + 140 - 72"));assertEquals(615,LpqRun.solveEquation("20 x 30 + 15"));assertThrows(IllegalArgumentException.class,()->LpqRun.solveEquation("615"));}
}
