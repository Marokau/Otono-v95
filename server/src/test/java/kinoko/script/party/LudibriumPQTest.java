package kinoko.script.party;
import kinoko.bot.*;
import kinoko.provider.*;
import kinoko.script.common.*;
import kinoko.server.field.*;
import kinoko.server.node.Client;
import kinoko.server.packet.NioBufferInPacket;
import kinoko.world.field.*;
import kinoko.world.field.drop.*;
import kinoko.world.field.mob.Mob;
import kinoko.world.item.*;
import kinoko.world.user.*;
import kinoko.util.Rect;
import org.junit.jupiter.api.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native LPQ content fixture. Positions/damage are test inputs, not an autonomous crew run. */
class LudibriumPQTest {
 final List<byte[]> observedMoves=new java.util.concurrent.CopyOnWriteArrayList<>();
 final List<String> dialogue=new ArrayList<>();
 Instance instance;InstanceFieldStorage storage;Field lobby,exit;List<User> party;Map<Integer,Integer> menu=new HashMap<>();
 @BeforeAll static void providers(){MapProvider.initialize();ItemProvider.initialize();MobProvider.initialize();SkillProvider.initialize();NpcProvider.initialize();ReactorProvider.initialize();RewardProvider.initialize();QuestProvider.initialize();EtcProvider.initialize();StringProvider.initialize();}
 @BeforeEach void setup(){
  instance=new Instance(23001,LudibriumPQ.EXIT,null,Instant.now().plusSeconds(LudibriumPQ.TIME_LIMIT));
  storage=InstanceFieldStorage.from(instance,LudibriumPQ.MAPS.stream().map(id->MapProvider.getMapInfo(id).orElseThrow()).toList());instance.setFieldStorage(storage);
  lobby=new Field(null,MapProvider.getMapInfo(LudibriumPQ.LOBBY).orElseThrow());exit=new Field(null,MapProvider.getMapInfo(LudibriumPQ.EXIT).orElseThrow());party=new ArrayList<>();
  for(int i=0;i<3;i++){
   var data=KpqCrewPreparation.create(10+i,50+i,"LpqTest"+i,KpqCrewPreparation.Role.SWORD);data.getCharacterStat().setLevel((short)30);
   var client=new Client(null,new CrewChannel(i==0?packet->{if(packet.getHeader()==kinoko.server.header.OutHeader.MobMove) observedMoves.add(packet.getData());}:null));var user=new User(client,data);client.setUser(user);user.setPartyInfo(new PartyInfo(73,i,i==0));user.setField(lobby);lobby.getUserPool().addUser(user);party.add(user);
  }
 }
 @AfterEach void cleanup(){storage.clear();lobby.getFieldEventFuture().cancel(true);exit.getFieldEventFuture().cancel(true);for(var u:party)((CrewChannel)u.getClient().getChannel()).finishAndReleaseAll();}
 void move(User user,int field){var target=field==LudibriumPQ.LOBBY?lobby:field==LudibriumPQ.EXIT?exit:storage.getFieldById(field).orElseThrow();
  Field from=user.getField();boolean leaving=from.getFieldStorage()!=target.getFieldStorage();if(from.getFieldStorage()==storage&&leaving)instance.clearRestrictedItems(user);
  from.removeUser(user,leaving);user.setField(target);user.setX(0);user.setY(0);target.getUserPool().addUser(user);if(target.getFieldStorage()==storage)instance.addUser(user);
 }
 ScriptManager script(User user){return (ScriptManager)Proxy.newProxyInstance(ScriptManager.class.getClassLoader(),new Class[]{ScriptManager.class},(proxy,method,args)->{
  if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
  switch(method.getName()){
   case "sayOk","sayNext","sayBoth"->{dialogue.add((String)args[0]);return null;}
   case "askYesNo"->{return true;}
   case "askMenu"->{return menu.getOrDefault(user.getCharacterId(),0);}
   case "partyWarpInstance"->{@SuppressWarnings("unchecked")var vars=(Map<String,String>)args[4];vars.forEach(instance::setVariable);for(var member:party)move(member,LudibriumPQ.STAGE_1);return null;}
   case "warp"->{move(user,(int)args[0]);return null;}
  }
  try{return method.invoke(new ScriptManagerImpl(user,user.getField(),user,"party2_play",2040034),args);}catch(InvocationTargetException e){throw e.getCause();}
 });}
 void enter(){LudibriumPQ.enter(script(party.getFirst()));assertEquals(LudibriumPQ.STAGE_1,party.getFirst().getFieldId());}
 void travel(User user,String portalName){var portal=user.getField().getPortalByName(portalName).orElseThrow();user.setX(portal.getX());user.setY(portal.getY());var decision=LudibriumPQ.resolvePortal(user,portal);assertNotNull(decision);assertTrue(decision.allowed(),decision.message());
  for(var member:decision.party()?List.copyOf(instance.getUsers()):List.of(user)){move(member,decision.field());var target=member.getField().getPortalByName(decision.portal()).orElseThrow();member.setX(target.getX());member.setY(target.getY());}
 }
 void breakBox(User user,kinoko.world.field.reactor.Reactor box){
  int hits=0;while(!box.isLastState()){assertTrue(++hits<10);assertTrue(box.tryHit(0));box.getField().getReactorPool().hitReactor(user,box,0);}
 }
 void killAll(int field){var f=storage.getFieldById(field).orElseThrow();var enemies=new ArrayList<Mob>();f.getMobPool().forEach(enemies::add);for(var mob:enemies)mob.damage(party.getFirst(),mob.getHp(),0);}
 @Test void observerSeesAlisharCastThenAttackTheCrewAndUnlockAllThreeSummonLevels() throws Exception {
  enter();
  Field field=storage.getFieldById(LudibriumPQ.BOSS).orElseThrow();
  User observer=party.getFirst();
  move(observer,LudibriumPQ.BOSS);
  var boss=field.getMobPool().getByTemplateId(9300012).orElseThrow();
  assertSame(observer,boss.getController());
  var client=new Client(null,new CrewChannel(null),true);
  var data=KpqCrewPreparation.create(92,93,"LpqTarget",KpqCrewPreparation.Role.SWORD);
  var bot=new User(client,data);client.setUser(bot);party.add(bot);
  bot.setPartyInfo(new PartyInfo(73,3,false));bot.setField(field);
  bot.setX(boss.getX()+160);bot.setY(boss.getY());field.getUserPool().addUser(bot);
  observer.setX(boss.getX()+900);observer.setY(boss.getY());
  boss.damage(bot,1,0);
  assertSame(bot,boss.getController());
  // A late packet from the former controller must not take back ownership.
  var oid=java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(boss.getId()).array();
  kinoko.handler.field.MobHandler.handleMobMove(observer,new NioBufferInPacket(oid));
  assertSame(bot,boss.getController());
  var encounter=new CrewEncounters();
  long now=System.currentTimeMillis();
  encounter.tick(field,List.of(bot),now);
  byte[] cast=observedMoves.getLast();
  assertEquals(22,Byte.toUnsignedInt(cast[9])>>1);
  assertTrue(Set.of(120,121).contains(Byte.toUnsignedInt(cast[10])));
  assertEquals(1,cast[11]);
  int before=bot.getHp(),mp=boss.getMp();
  encounter.tick(field,List.of(bot),now+1000);
  assertEquals(13,Byte.toUnsignedInt(observedMoves.getLast()[9])>>1);
  assertEquals(mp-5,boss.getMp());
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
  while(bot.getHp()==before && System.nanoTime()<deadline)Thread.sleep(25);
  assertTrue(bot.getHp()<before,"Alishar's delayed magic attack must hit the bot while the observer stays out of range");
  for(int i=0;i<3;i++){
   var summon=boss.getSkill(200,29+i).orElseThrow();
   int threshold=boss.getMaxHp()*(75-25*i)/100;
   boss.setHp(threshold+1);assertFalse(boss.canUseSkill(summon));
   boss.setHp(threshold);assertTrue(boss.canUseSkill(summon));
   // Isolate this phase's naturally eligible cast, independently of random skill selection.
   for(var skill:boss.getTemplate().getSkills())boss.setSkillOnCooltime(skill,Instant.MAX);
   boss.setSkillOnCooltime(summon,Instant.MIN);
   boss.setNextSkillUse(Instant.MIN);
   int countBefore=field.getMobPool().getCount();
   encounter.tick(field,List.of(bot),now+10000L*(i+1));
   var summons=SkillProvider.getMobSummonInfoByLevel(29+i).orElseThrow().getSummons();
   assertEquals(countBefore+summons.size(),field.getMobPool().getCount());
   for(int templateId:summons)assertTrue(field.getMobPool().getByTemplateId(templateId).isPresent());
   if(i<2)boss.setSkillOnCooltime(boss.getSkill(200,30+i).orElseThrow(),Instant.MIN);
  }
 }
 @Test void fullNativeRouteFromEntryThroughIndividualClearRewards(){
  enter();assertEquals(3600,LudibriumPQ.TIME_LIMIT);assertEquals(14,LudibriumPQ.darkRemaining(instance));
  User leader=party.getFirst();killAll(LudibriumPQ.STAGE_1);
  assertEquals("1",instance.getVariable("gate1").orElseThrow());
  assertTrue(leader.getField().getDropPool().getBy(d->d.getItem()!=null&&d.getItem().getItemId()==LudibriumPQ.PASS).isEmpty(),"Ratz stage is kill-all, not the later pass stage");
  travel(leader,"next00");assertEquals(LudibriumPQ.BOX_STAGE,leader.getFieldId());
  var boxes=new ArrayList<kinoko.world.field.reactor.Reactor>();leader.getField().getReactorPool().forEach(boxes::add);assertEquals(10,boxes.size());
  for(var box:boxes){breakBox(leader,box);var drop=leader.getField().getDropPool().getBy(d->d.getSource()==box).orElseThrow();leader.getField().getDropPool().pickUpDrop(leader,drop,DropLeaveType.PICKED_UP_BY_USER,0);}
  assertEquals(0,leader.getInventoryManager().getItemCount(LudibriumPQ.PASS));assertEquals("10",instance.getVariable("passes").orElseThrow());travel(leader,"next00");
  for(int room=1;room<=5;room++){
   travel(leader,"in0"+room);assertEquals(room==5?2:3,leader.getField().getMobPool().getCount());killAll(LudibriumPQ.STAGE_2+room);travel(leader,"out00");assertEquals("in0"+room,leader.getField().getPortalByName("in0"+room).orElseThrow().getPortalName());
  }
  assertEquals("1",instance.getVariable("gate2").orElseThrow());travel(leader,"next00");
  String maze=instance.getVariable("maze").orElseThrow();for(int row=0;row<15;row++)travel(leader,String.format("h%03d",row*3+maze.charAt(row)-'0'));
  assertEquals("1",instance.getVariable("gate3").orElseThrow());travel(leader,"next00");killAll(LudibriumPQ.STAGE_4);LudibriumPQ.play(script(leader));travel(leader,"next00");
  LudibriumPQ.play(script(leader));int answer=Integer.parseInt(instance.getVariable("math_answer").orElseThrow());String digits=String.valueOf(answer);
  for(int i=0;i<3;i++){Rect area=leader.getField().getMapInfo().getAreas().get(digits.charAt(i)-'1');party.get(i).setX(area.getCenterX());party.get(i).setY(area.getCenterY());}
  dialogue.clear();LudibriumPQ.play(script(leader));assertTrue(dialogue.isEmpty(),"Correct platforms clear without an extra NPC window");travel(leader,"next00");killAll(LudibriumPQ.BOSS);assertEquals("1",instance.getVariable("boss_defeated").orElseThrow());
  // The native six-chest UI is required, not an NPC-only substitute.
  assertEquals(6,instance.getRewards().orElseThrow().boxCount());
  LudibriumPQ.play(script(leader));assertEquals(LudibriumPQ.BOSS,leader.getFieldId(),"Cannot leave before receiving selected reward");assertEquals(0,LudibriumPQ.recordNumber(leader,"cmp"));
  for(int i=0;i<party.size();i++)KerningPQRewards.select(party.get(i),new NioBufferInPacket(new byte[]{(byte)i}));
  for(User member:party){int before=member.getCharacterStat().getExp();KerningPQRewards.claim(member,new NioBufferInPacket(new byte[0]));assertTrue(instance.getRewards().orElseThrow().hasClaimed(member.getCharacterId()));KerningPQRewards.claim(member,new NioBufferInPacket(new byte[0]));assertEquals(1,LudibriumPQ.recordNumber(member,"cmp"));assertEquals(before,member.getCharacterStat().getExp(),"Chest claim grants the item");}
  for(User member:party){int before=member.getCharacterStat().getExp();LudibriumPQ.play(script(member));assertEquals(before+7000,member.getCharacterStat().getExp(),"Clear EXP on exit");assertEquals(LudibriumPQ.EXIT,member.getFieldId());assertEquals(1,LudibriumPQ.recordNumber(member,"cmp"));LudibriumPQ.out(script(member));assertEquals(LudibriumPQ.LOBBY,member.getFieldId());}
  int afterClear=leader.getCharacterStat().getExp();move(leader,LudibriumPQ.BOSS);LudibriumPQ.play(script(leader));assertEquals(afterClear,leader.getCharacterStat().getExp(),"No duplicate exit EXP");assertEquals(1,LudibriumPQ.recordNumber(leader,"cmp"),"Re-entry must not duplicate clear credit");
 }
 @Test void balloonDialogueDependsOnStageAndPartyPresence(){
  enter();
  User leader=party.getFirst();
  LudibriumPQ.play(script(leader));
  LudibriumPQ.play(script(leader));
  LudibriumPQ.play(script(party.get(1)));
  assertEquals(List.of(PqDialogue.LPQ_FIRST,PqDialogue.LPQ_RATS_REMINDER,PqDialogue.LPQ_FIRST),dialogue);
  dialogue.clear();
  killAll(LudibriumPQ.STAGE_1);
  LudibriumPQ.play(script(leader));
  assertEquals(List.of(PqDialogue.LPQ_RATS_CLEAR,PqDialogue.LPQ_RATS_PORTAL),dialogue);
  dialogue.clear();
  move(leader,LudibriumPQ.STAGE_2);
  LudibriumPQ.play(script(leader));
  assertEquals(List.of(PqDialogue.LPQ_MISSING_MEMBERS),dialogue);
  dialogue.clear();
  for(User member:party)move(member,LudibriumPQ.STAGE_4);
  killAll(LudibriumPQ.STAGE_4);
  LudibriumPQ.play(script(leader));
  assertEquals(List.of(PqDialogue.LPQ_ROMBOTS_CLEAR,PqDialogue.LPQ_PORTAL_OPEN),dialogue);
 }
 @Test void recordedEquationsUseTheSameTextForTheBannerAndHint(){
  for(var example:Map.of("400 + 140 - 72","400+140-72","20 x 30 + 15","20*30+15").entrySet()){
   instance.setVariable("equation",example.getKey());
   assertEquals(example.getValue(),LudibriumPQ.displayEquation(instance));
   assertTrue(PqDialogue.lpqMathHint(LudibriumPQ.displayEquation(instance)).contains(example.getValue()+" = ?"));
  }
 }
 @Test void gatesRejectEarlyProgressAndMazeSkipsAndResetOnlyTheWrongPlayer(){
  enter();User leader=party.getFirst();var portal=leader.getField().getPortalByName("next00").orElseThrow();leader.setX(portal.getX());leader.setY(portal.getY());assertFalse(LudibriumPQ.resolvePortal(leader,portal).allowed());
  move(leader,LudibriumPQ.STAGE_3);var top=leader.getField().getPortalByName("h044").orElseThrow();leader.setX(top.getX());leader.setY(top.getY());assertFalse(LudibriumPQ.resolvePortal(leader,top).allowed());
  int wrong=(instance.getVariable("maze").orElseThrow().charAt(0)-'0'+1)%3;var first=leader.getField().getPortalByName(String.format("h%03d",wrong)).orElseThrow();leader.setX(first.getX());leader.setY(first.getY());var result=LudibriumPQ.resolvePortal(leader,first);assertTrue(result.allowed());assertFalse(result.party());assertEquals("st00",result.portal());assertEquals(0,LudibriumPQ.count(instance,"maze_progress_"+leader.getCharacterId()));
 }
 @Test void questionsHaveThreeDistinctNonzeroDigitsAndRequireExactlyThosePlatforms(){
  for(int n=0;n<100;n++){int[] question=LudibriumPQ.newQuestion();assertEquals(question[0]*question[1]+question[2],question[3]);String digits=String.valueOf(question[3]);assertEquals(3,digits.length());assertFalse(digits.contains("0"));assertEquals(3,digits.chars().distinct().count());StringBuilder occupancy=new StringBuilder("000000000");digits.chars().forEach(c->occupancy.setCharAt(c-'1','1'));assertTrue(LudibriumPQ.matchesDigits(occupancy.toString(),question[3]));assertFalse(LudibriumPQ.matchesDigits("111111111",question[3]));}
 }
 @Test void glassesAreRepeatableEveryThirtyFiveClearsButCannotBeDuplicated(){
  User leader=party.getFirst();LudibriumPQ.saveRecord(leader,new LinkedHashMap<>(Map.of("cmp","34","have","0")));menu.put(leader.getCharacterId(),2);LudibriumPQ.enter(script(leader));assertEquals(0,leader.getInventoryManager().getItemCount(LudibriumPQ.GLASSES));
  LudibriumPQ.saveRecord(leader,new LinkedHashMap<>(Map.of("cmp","35","have","0")));LudibriumPQ.enter(script(leader));assertEquals(1,leader.getInventoryManager().getItemCount(LudibriumPQ.GLASSES));LudibriumPQ.enter(script(leader));assertEquals(1,leader.getInventoryManager().getItemCount(LudibriumPQ.GLASSES));assertEquals(1,LudibriumPQ.recordNumber(leader,"have"));
 }
 @Test void virtualPassesWorkWithFullEtcInventoryAndCannotBeCountedTwice(){
  enter();User leader=party.getFirst();var inventory=leader.getInventoryManager().getEtcInventory();
  for(int id=4000000;inventory.getItems().size()<24;id++){var info=ItemProvider.getItemInfo(id);if(info.isPresent())inventory.putItem((short)(inventory.getItems().size()+1),info.get().createItem(leader.getNextItemSn()));}
  move(leader,LudibriumPQ.BOX_STAGE);var box=leader.getField().getReactorPool().getByTemplateId(2202003).orElseThrow();
  breakBox(leader,box);Drop drop=leader.getField().getDropPool().getBy(d->d.getSource()==box).orElseThrow();
  leader.getField().getDropPool().pickUpDrop(leader,drop,DropLeaveType.PICKED_UP_BY_USER,0);
  assertEquals(1,LudibriumPQ.count(instance,"passes"));assertEquals(24,inventory.getItems().size());
  assertTrue(LudibriumPQ.pickUpPass(leader,drop,DropLeaveType.PICKED_UP_BY_USER,0));assertEquals(1,LudibriumPQ.count(instance,"passes"));
  leader.getField().getReactorPool().expireReactors(Instant.now().plusSeconds(5));assertTrue(box.isLastState(),"A pass box must not respawn after one second");
  LudibriumPQ.passBoxBroken(leader,box);assertTrue(leader.getField().getDropPool().getBy(d->d.getSource()==box).isEmpty(),"Repeated callbacks cannot duplicate the pass");
 }
 @Test void mazeUsesInstalledFixedDestinationsInsteadOfRandomizingEachRun(){
  enter();var map=storage.getFieldById(LudibriumPQ.STAGE_3).orElseThrow();String route=instance.getVariable("maze").orElseThrow();assertEquals(15,route.length());
  for(int row=0;row<15;row++)for(int column=0;column<3;column++){
   var portal=map.getPortalByName(String.format("h%03d",row*3+column)).orElseThrow();assertEquals(column==route.charAt(row)-'0',!portal.getDestinationPortalName().equals("st00"));
  }
 }
 @Test void equationWaitsForTheWholeRemainingParty(){
  enter();move(party.get(0),LudibriumPQ.STAGE_5);move(party.get(1),LudibriumPQ.STAGE_5);move(party.get(2),LudibriumPQ.STAGE_4);
  LudibriumPQ.play(script(party.getFirst()));assertTrue(instance.getVariable("equation").isEmpty());
  move(party.get(2),LudibriumPQ.STAGE_5);LudibriumPQ.play(script(party.getFirst()));assertTrue(instance.getVariable("equation").isPresent());
 }
 @Test void entryRejectsTwoMembersAndUnderlevelMembers(){
  User leader=party.getFirst();lobby.getUserPool().removeUser(party.get(2));LudibriumPQ.enter(script(leader));assertEquals(LudibriumPQ.LOBBY,leader.getFieldId());
  lobby.getUserPool().addUser(party.get(2));party.get(2).getCharacterStat().setLevel((short)29);LudibriumPQ.enter(script(leader));assertEquals(LudibriumPQ.LOBBY,leader.getFieldId());
 }
 @Test void aFullEquipInventoryDoesNotSpendTheGlassesEntitlement(){
  User leader=party.getFirst();var bag=leader.getInventoryManager().getEquipInventory();var hat=ItemProvider.getItemInfo(1002000).orElseThrow();
  for(int n=0;n<24;n++)bag.putItem((short)(n+1),hat.createItem(leader.getNextItemSn()));
  LudibriumPQ.saveRecord(leader,new LinkedHashMap<>(Map.of("cmp","35","have","0")));menu.put(leader.getCharacterId(),2);LudibriumPQ.enter(script(leader));assertEquals(0,LudibriumPQ.recordNumber(leader,"have"));
  var slot=bag.getItems().entrySet().iterator().next();assertTrue(bag.removeItem(slot.getKey(),slot.getValue()));
  LudibriumPQ.enter(script(leader));assertEquals(1,LudibriumPQ.recordNumber(leader,"have"));assertEquals(1,leader.getInventoryManager().getItemCount(LudibriumPQ.GLASSES));
 }}
