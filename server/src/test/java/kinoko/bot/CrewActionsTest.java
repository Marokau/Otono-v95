package kinoko.bot;
import kinoko.provider.*;
import kinoko.server.node.Client;
import kinoko.world.field.Field;
import kinoko.world.field.drop.*;
import kinoko.world.field.mob.Mob;
import kinoko.world.item.*;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterTemporaryStat;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class CrewActionsTest {
 Field field;User user;
 @BeforeAll static void providers(){MapProvider.initialize();ItemProvider.initialize();SkillProvider.initialize();MobProvider.initialize();StringProvider.initialize();RewardProvider.initialize();}
 User create(KpqCrewPreparation.Role role){
  field=new Field(null,MapProvider.getMapInfo(910340500).orElseThrow());
  var client=new Client(null,new CrewChannel(null),true);user=new User(client,KpqCrewPreparation.create(1,8765,"ActionTest",role));client.setUser(user);user.setField(field);KpqCrewPreparation.equip(user,role);user.setX(0);user.setY(0);return user;
 }
 @AfterEach void cleanup(){CrewMotion.cancel(user);field.getFieldEventFuture().cancel(true);((CrewChannel)user.getClient().getChannel()).finishAndReleaseAll();}
 @Test void rangedAttacksConsumeNativeArrowsAndMpAndCannotHitAcrossTheMap(){
  create(KpqCrewPreparation.Role.ARCHER);var boss=new Mob(MobProvider.getMobTemplate(9300003).orElseThrow(),null,150,0,0);field.getMobPool().addMob(boss);
  int arrows=user.getInventoryManager().getItemCount(KpqCrewPreparation.BOW_ARROW),mp=user.getMp(),hp=boss.getHp();
  for(int i=0;i<5;i++)assertTrue(CrewActions.attack(user,boss,KpqCrewPreparation.Role.ARCHER));
  assertEquals(arrows-10,user.getInventoryManager().getItemCount(KpqCrewPreparation.BOW_ARROW));assertTrue(user.getMp()<mp);assertTrue(boss.getHp()<hp);
  user.setX(-500);int current=user.getInventoryManager().getItemCount(KpqCrewPreparation.BOW_ARROW);assertFalse(CrewActions.attack(user,boss,KpqCrewPreparation.Role.ARCHER));assertEquals(current,user.getInventoryManager().getItemCount(KpqCrewPreparation.BOW_ARROW));
 }
 @Test void magicGuardAndPotionsUseNormalSkillAndItemHandlers(){
  create(KpqCrewPreparation.Role.MAGE);assertTrue(CrewActions.buff(user,2001002));assertTrue(user.getSecondaryStat().hasOption(CharacterTemporaryStat.MagicGuard));
  var boss=new Mob(MobProvider.getMobTemplate(9300003).orElseThrow(),null,10,0,0);field.getMobPool().addMob(boss);
  int hp=user.getHp(),mp=user.getMp();kinoko.handler.user.HitHandler.handleAutomatedHit(user,boss,-1,100);
  assertTrue(user.getHp()<hp&&user.getHp()>hp-100);assertTrue(user.getMp()<mp);
  assertTrue(CrewActions.potion(user));assertEquals(199,user.getInventoryManager().getItemCount(KpqCrewPreparation.GINGER_ALE));assertTrue(user.getHp()>hp-100);
 }
 @Test void couponsRequirePhysicalProximityAndPassDroppingUsesTheNativeInventoryHandler(){
  create(KpqCrewPreparation.Role.SWORD);Item coupon=ItemProvider.getItemInfo(4001007).orElseThrow().createItem(1,1,ItemVariationOption.NONE);
  var drop=Drop.item(DropOwnType.NOOWN,user,coupon,user.getCharacterId());field.getDropPool().addDrop(drop,DropEnterType.CREATE,400,0,0);
  assertFalse(CrewActions.pickUp(user,drop));assertEquals(0,user.getInventoryManager().getItemCount(4001007));
  user.setX(drop.getX());user.setY(drop.getY());assertTrue(CrewActions.pickUp(user,drop));assertEquals(1,user.getInventoryManager().getItemCount(4001007));
  assertTrue(CrewActions.drop(user,4001007,1));assertEquals(0,user.getInventoryManager().getItemCount(4001007));assertTrue(field.getDropPool().getBy(d->d.getItem()!=null&&d.getItem().getItemId()==4001007).isPresent());
 }
}
