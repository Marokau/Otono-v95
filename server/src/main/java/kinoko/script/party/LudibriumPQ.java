package kinoko.script.party;

import kinoko.packet.field.FieldEffectPacket;
import kinoko.packet.world.*;
import kinoko.provider.*;
import kinoko.provider.map.PortalInfo;
import kinoko.script.common.*;
import kinoko.server.field.*;
import kinoko.util.Util;
import kinoko.world.field.Field;
import kinoko.world.field.drop.*;
import kinoko.world.field.mob.Mob;
import kinoko.world.item.ItemVariationOption;
import kinoko.world.quest.*;
import kinoko.world.user.User;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Native v95 route verified against the supplied July 2011 footage and installed map portals. */
public final class LudibriumPQ extends ScriptHandler {
 public static final int LOBBY=221023300,EXIT=922010000,STAGE_1=922010100,BOX_STAGE=922010200,STAGE_2=922010400,STAGE_3=922010600,STAGE_4=922010700,STAGE_5=922010800,BOSS=922010900;
 public static final int PASS=4001022,KEY=4001023,PUPPET=4001454,GLASSES=1022073,RECORD=1202;
 public static final int MIN_LEVEL=30,MAX_LEVEL=200,TIME_LIMIT=60*60;
 // July GMS shows this separate award on the transition out of the boss room.
 public static final int CLEAR_EXP=7000;
 private static final int RED_SIGN_WEATHER = 5120018;
 // Sniper Potion is visibly awarded at 6:22. Remaining consumables/quantities/weights are provisional.
 public static final List<Integer> REWARD_ITEMS=List.of(2002005,2000002,2000003,2000006,2000004,2022000);
 public static final List<Integer> MAPS=List.of(STAGE_1,BOX_STAGE,STAGE_2,922010401,922010402,922010403,922010404,922010405,STAGE_3,STAGE_4,STAGE_5,BOSS);
 public record Travel(int field,String portal,boolean party,boolean allowed,String message) {
  static Travel deny(String message){return new Travel(0,"",false,false,message);}
 }
 public static Optional<Instance> instance(User user){
  return user.getField().getFieldStorage() instanceof InstanceFieldStorage storage&&storage.getInstance().getVariable("lpq").orElse("").equals("1")?Optional.of(storage.getInstance()):Optional.empty();
 }
 static String value(Instance instance,String key){return instance.getVariable(key).orElse("");}
 static int count(Instance instance,String key){String text=value(instance,key);return text.matches("\\d+")?Integer.parseInt(text):0;}
 public static boolean participant(Instance instance,User user){return Arrays.asList(value(instance,"roster").split(",")).contains(String.valueOf(user.getCharacterId()))&&user.getPartyId()==count(instance,"party_id")&&user.getHp()>0;}
 static List<User> members(Instance instance){return instance.getUsers().stream().filter(user->participant(instance,user)).toList();}
 static boolean cleared(Instance instance,int stage){return value(instance,"gate"+stage).equals("1");}
 static void clear(Instance instance,int stage){
  if(cleared(instance,stage))return;instance.setVariable("gate"+stage,"1");
  int map=switch(stage){case 1->STAGE_1;case 2->STAGE_2;case 3->STAGE_3;case 4->STAGE_4;case 5->STAGE_5;default->BOSS;};
  Field field=instance.getFieldStorage().getFieldById(map).orElseThrow();field.setObjectState("gate",0);
  field.broadcastPacket(FieldEffectPacket.screen("quest/party/clear"));field.broadcastPacket(FieldEffectPacket.sound("Party1/Clear"));
  for(User member:members(instance)){member.addExp(100);member.write(MessagePacket.incExp(100,0,true,true));}
  notice(instance,"LPQ: Stage "+stageLabel(map)+" is clear. Enter the opened door when ready.");
 }
 static String stageLabel(int field){return switch(field){case STAGE_1->"One";case BOX_STAGE->"Two";case STAGE_2->"Three";case STAGE_3->"Four";case STAGE_4->"Five";case STAGE_5->"Six";default->"Boss";};}
 static void notice(Instance instance,String text){for(User member:members(instance))member.write(WvsContext.scriptProgressMessage(text));}
 @Script("party2_enter") public static void enter(ScriptManager sm){
  if(sm.getFieldId()!=LOBBY)return;
  int choice=sm.askMenu(PqDialogue.LPQ_LOBBY,Map.of(0,"I want to participate in the party quest.",1,"I want to find party members.",2,"I want to receive the Broken Glasses.",3,"I would like to hear more details.",4,"\r\n#eBotPQ Menu#n"));
  if(choice==4){
   int option=sm.askMenu("#eBotPQ Menu#n\r\nThree level-35 companions will help you through LPQ. You remain party leader.\r\n",Map.of(0,"Assemble an LPQ bot party for me",1,"Dismiss my LPQ crew",2,"Close"));
   if(option==0)sm.sayOk(kinoko.bot.LpqCrewService.assemble(sm.getUser()));else if(option==1){kinoko.bot.LpqCrewService.dismiss(sm.getUser());sm.sayOk("Your LPQ crew has been dismissed.");}return;
  }
  if(choice==0){
   User leader=sm.getUser();if(!leader.isPartyBoss()){sm.sayOk("Please ask your party leader to register.");return;}
   var party=sm.getField().getUserPool().getPartyMembers(leader.getPartyId());
   if(party.size()<3||party.size()>6||!sm.checkParty(3,user->user.getHp()>0&&user.getLevel()>=MIN_LEVEL&&user.getLevel()<=MAX_LEVEL)){
    sm.sayOk("Bring 3-6 living party members, all level 30-200, to this lobby.");return;
   }
   kinoko.bot.LpqCrewService.prepareEntry(leader);
   for(User member:party)for(int item:List.of(PASS,KEY,PUPPET))new ScriptManagerImpl(member,sm.getField(),member,"party2_enter",2040034).removeItem(item);
   sm.partyWarpInstance(MAPS,"st00",EXIT,TIME_LIMIT,Map.of("lpq","1","party_id",String.valueOf(leader.getPartyId()),"roster",party.stream().map(u->String.valueOf(u.getCharacterId())).collect(Collectors.joining(",")),"started",String.valueOf(Instant.now().getEpochSecond())));
   var instance=instance(leader).orElseThrow();initialize(instance);
  }else if(choice==1)sm.sayOk("Invite other adventurers to your party here. Otono also offers prepared companions under the BotPQ Menu.");
  else if(choice==3)sm.sayOk("#bLevels:#k 30-200\r\n#bParty size:#k 3-6\r\n#bTime limit:#k 60 minutes\r\n\r\nDefeat all Ratz and Black Ratz, collect ten passes from boxes, clear five dark rooms, follow the box route, defeat the Rombots, solve the number-platform equation and defeat Alishar. Bring physical and magic attackers, potions and All Cures. Speak to the Red Sign after 35 completions for Broken Glasses.");
  else if(choice==2)claimGlasses(sm);
  else sm.sayOk("You have completed Dimensional Crack "+recordNumber(sm.getUser(),"cmp")+" times and claimed "+recordNumber(sm.getUser(),"have")+" pair(s) of Broken Glasses.");
 }
 public static void initialize(Instance instance){
  instance.setRestrictedItems(List.of(PASS,KEY,PUPPET));instance.setVariable("ready_after",String.valueOf(Instant.now().plusSeconds(5).getEpochSecond()));
  // The installed v95 map supplies the fixed route. Do not import the later randomized puzzle.
  var mazeField=instance.getFieldStorage().getFieldById(STAGE_3).orElseThrow();var route=new StringBuilder();
  for(int row=0;row<15;row++){
   final int current=row;var correct=mazeField.getMapInfo().getPortalInfos().stream().filter(p->p.getPortalName().matches("h\\d{3}")&&Integer.parseInt(p.getPortalName().substring(1))/3==current&&!p.getDestinationPortalName().equals("st00")).toList();
   if(correct.size()!=1)throw new IllegalStateException("Invalid native LPQ box row "+row);
   route.append(Integer.parseInt(correct.getFirst().getPortalName().substring(1))%3);
  }instance.setVariable("maze",route.toString());
  for(int id:MAPS){var field=instance.getFieldStorage().getFieldById(id).orElseThrow();field.setMobSpawn(false);field.getMobPool().respawnMobs(Instant.MAX);}
  for(int room=1;room<=5;room++){
   var field=instance.getFieldStorage().getFieldById(STAGE_2+room).orElseThrow();int needed=room==5?2:3;
   var seed=field.getMapInfo().getLifeInfos().stream().filter(life->life.getLifeType()==kinoko.provider.map.LifeType.MOB).findFirst().orElseThrow();
   int original=field.getMobPool().getCount();for(int n=original;n<needed;n++){
    int x=n==original?300:-1200;var fh=field.getMapInfo().getFootholds().stream().filter(f->!f.isWall()&&x>=f.getX1()&&x<=f.getX2()).min(Comparator.comparingInt(f->Math.abs(f.getYFromX(x)-seed.getCy()))).orElseThrow();var mob=new Mob(MobProvider.getMobTemplate(seed.getTemplateId()).orElseThrow(),null,x,fh.getYFromX(x),fh.getSn());field.getMobPool().addMob(mob);
   }
  }
  instance.setMobDefeatedHandler(mob->onDeath(instance,mob));instance.setUpdateHandler(LudibriumPQ::update);
 }
 @Script("party2_play") public static void play(ScriptManager sm){
  var result=instance(sm.getUser());if(result.isEmpty()){sm.sayOk("Please enter Dimensional Crack with your party first.");return;}
  var instance=result.get();if(!participant(instance,sm.getUser()))return;
  switch(sm.getFieldId()){
   case STAGE_1 -> {
    if(cleared(instance,1)){
     clearedDialogue(sm,PqDialogue.LPQ_RATS_CLEAR,PqDialogue.LPQ_RATS_PORTAL);
    }else{
     String key="red_intro_"+sm.getUser().getCharacterId();
     boolean first=value(instance,key).isEmpty();
     instance.setVariable(key,"1");
     sm.sayNext(first?PqDialogue.LPQ_FIRST:PqDialogue.LPQ_RATS_REMINDER);
    }
   }
   case BOX_STAGE -> { if(value(instance,"box_gate").equals("1"))clearedDialogue(sm,PqDialogue.LPQ_BOXES_CLEAR,PqDialogue.LPQ_PORTAL_OPEN);else sm.sayOk(PqDialogue.lpqBubble(BOX_STAGE)+" Collected: "+count(instance,"passes")+"/10."); }
   case STAGE_2 -> {
    if(members(instance).stream().anyMatch(user->user.getField()!=sm.getField()))sm.sayNext(PqDialogue.LPQ_MISSING_MEMBERS);
    else if(cleared(instance,2))clearedDialogue(sm,PqDialogue.LPQ_EYES_CLEAR,PqDialogue.LPQ_PORTAL_OPEN);
    else sm.sayOk("Clear all five dark rooms. The upper three favor magic attacks; the lower two favor physical attacks. The lowest room has two monsters; the others have three. Mark occupied doors with mesos if you wish. Monsters remaining: "+darkRemaining(instance)+"."); }
   case STAGE_3 -> sm.sayOk("Figure out the hidden code for the box and get to the top. The box route is fixed. A wrong choice returns you to the start. Once someone reaches the top, the party gathers beside the exit.");
   case STAGE_4 -> {
    if(members(instance).stream().anyMatch(user->user.getField()!=sm.getField())){sm.sayNext(PqDialogue.LPQ_MISSING_MEMBERS);return;}
    if(!sm.getUser().isPartyBoss()){sm.sayOk("Defeat all four Rombots and their summoned monsters, then ask your leader to speak with me.");return;}
    if(count(instance,"rombots")<4||!sm.getField().getMobPool().isEmpty()){sm.sayOk("Defeat all four Rombots and every monster remaining in this room first.");return;}clear(instance,4);
    clearedDialogue(sm,PqDialogue.LPQ_ROMBOTS_CLEAR,PqDialogue.LPQ_PORTAL_OPEN);
   }
   case STAGE_5 -> math(sm,instance);
   case BOSS -> finish(sm,instance);
  }
 }
 static void clearedDialogue(ScriptManager sm,String first,String second){sm.sayNext(first);sm.sayBoth(second);}
 public static int darkRemaining(Instance instance){return java.util.stream.IntStream.rangeClosed(1,5).map(room->instance.getFieldStorage().getFieldById(STAGE_2+room).orElseThrow().getMobPool().getCount()).sum();}
 private static void onDeath(Instance instance,Mob mob){
  if(mob.getField().getFieldId()==STAGE_1&&mob.getField().getMobPool().isEmpty())clear(instance,1);
  if(mob.getField().getFieldId()>=STAGE_2+1&&mob.getField().getFieldId()<=STAGE_2+5){
   notice(instance,"LPQ: Dark room "+(mob.getField().getFieldId()-STAGE_2)+" has "+mob.getField().getMobPool().getCount()+" monsters remaining. Total: "+darkRemaining(instance)+".");
   if(darkRemaining(instance)==0)clear(instance,2);
  }
  if(mob.getField().getFieldId()==STAGE_4&&mob.getTemplateId()==9300010)instance.setVariable("rombots",String.valueOf(count(instance,"rombots")+1));
  if(mob.getField().getFieldId()==BOSS&&mob.getTemplateId()==9300012){instance.setVariable("boss_defeated","1");KerningPQRewards.prepare(instance);for(User member:members(instance))KerningPQRewards.show(member);
   notice(instance,"LPQ: Alishar is defeated. Choose a treasure box, receive your prize, then speak to the Violet Balloon to leave.");}
 }
 /** Only the native v95 Stage Two pass boxes use this lifecycle; other reactors keep their native behavior. */
 public static boolean isPassBox(kinoko.world.field.reactor.Reactor reactor){
  return reactor.getTemplateId()==2202003&&reactor.getField().getFieldId()==BOX_STAGE&&reactor.getField().getFieldStorage() instanceof InstanceFieldStorage storage&&value(storage.getInstance(),"lpq").equals("1");
 }
 public static void passBoxBroken(User user,kinoko.world.field.reactor.Reactor reactor){
  if(!isPassBox(reactor)||!reactor.isLastState())return;var result=instance(user);if(result.isEmpty()||user.getField()!=reactor.getField())return;var instance=result.get();
  if(!participant(instance,user))return;String key="box_drop_"+reactor.getId();if(value(instance,key).equals("1"))return;instance.setVariable(key,"1");
  var item=ItemProvider.getItemInfo(PASS).orElseThrow().createItem(user.getNextItemSn(),1,ItemVariationOption.NORMAL);
  reactor.getField().getDropPool().addDrop(Drop.item(DropOwnType.PARTYOWN,reactor,item,user.getPartyId(),0),DropEnterType.CREATE,reactor.getX(),reactor.getY()-20,0);
 }
 public static boolean suppressRatPass(Mob mob,int itemId){return itemId==PASS&&mob.getField().getFieldId()==STAGE_1&&mob.getField().getFieldStorage() instanceof InstanceFieldStorage storage&&value(storage.getInstance(),"lpq").equals("1");}
 /** Genuine Stage Two box passes count for the party without occupying a bag slot. */
 public static boolean pickUpPass(User user,Drop drop,DropLeaveType leave,int pet){
  var result=instance(user);if(result.isEmpty()||user.getFieldId()!=BOX_STAGE||drop.getItem()==null||drop.getItem().getItemId()!=PASS)return false;
  var instance=result.get();if(!participant(instance,user)||!drop.canPickUp(user))return true;
  if(!(drop.getSource() instanceof kinoko.world.field.reactor.Reactor reactor)||!isPassBox(reactor)||!value(instance,"box_drop_"+reactor.getId()).equals("1"))return false;
  if(!user.getField().getDropPool().removeDrop(drop,leave,user.getCharacterId(),pet,0))return true;
  int total=Math.min(10,count(instance,"passes")+drop.getItem().getQuantity());instance.setVariable("passes",String.valueOf(total));
  user.getField().blowWeather(RED_SIGN_WEATHER,"You have collected "+total+" Passes.",5);
  if(total==10&&!value(instance,"box_gate").equals("1")){
   instance.setVariable("box_gate","1");user.getField().setObjectState("gate",0);user.getField().broadcastPacket(FieldEffectPacket.screen("quest/party/clear"));user.getField().broadcastPacket(FieldEffectPacket.sound("Party1/Clear"));
   for(User member:members(instance)){member.addExp(100);member.write(MessagePacket.incExp(100,0,true,true));}
   notice(instance,"LPQ: Stage Two is clear. Enter the opened door when ready.");
  }return true;
 }
 public static Travel resolvePortal(User user,PortalInfo portal){
  var result=instance(user);if(result.isEmpty())return null;var instance=result.get();
  if(!participant(instance,user)||value(instance,"failed").equals("1"))return Travel.deny("You are not an active member of this run.");
  if(Math.abs(user.getX()-portal.getX())>100||Math.abs(user.getY()-portal.getY())>120)return Travel.deny("Move to the portal before entering it.");
  int field=user.getFieldId();String name=portal.getPortalName();
  if(field==STAGE_1&&name.equals("next00"))return cleared(instance,1)?new Travel(BOX_STAGE,"st00",true,true,""):Travel.deny("Eliminate all Ratz and Black Ratz first.");
  if(field==BOX_STAGE&&name.equals("next00"))return value(instance,"box_gate").equals("1")?new Travel(STAGE_2,"st00",true,true,""):Travel.deny("Collect ten passes from the boxes first.");
  if(field==STAGE_2){
   if(name.matches("in0[1-5]"))return new Travel(STAGE_2+Integer.parseInt(name.substring(2)),portal.getDestinationPortalName(),false,true,"");
   if(name.equals("next00"))return cleared(instance,2)?new Travel(STAGE_3,"st00",true,true,""):Travel.deny("Clear every dark room first.");
  }
  if(field>=STAGE_2+1&&field<=STAGE_2+5&&name.equals("out00"))return new Travel(STAGE_2,"in0"+(field-STAGE_2),false,true,"");
  if(field==STAGE_3){
   if(name.matches("h\\d{3}")){
    int index=Integer.parseInt(name.substring(1));if(index>=45)return Travel.deny("This box is not part of the route.");
    int row=index/3,column=index%3;String progress="maze_progress_"+user.getCharacterId();
    if(row!=count(instance,progress))return Travel.deny("Complete the preceding rows before trying this box.");
    boolean correct=!portal.getDestinationPortalName().equals("st00");
    if(!correct){instance.setVariable(progress,"0");user.write(WvsContext.scriptProgressMessage("LPQ: That box was incorrect. Try again from the start."));return new Travel(STAGE_3,"st00",false,true,"");}
    // Fixed native route: no later-era glowing-box or random-route messages.
    instance.setVariable("row"+row,String.valueOf(column));instance.setVariable(progress,String.valueOf(row+1));
    if(row==14){clear(instance,3);return new Travel(STAGE_3,"next00",true,true,"");}
    return new Travel(STAGE_3,portal.getDestinationPortalName(),false,true,"");
   }
   if(name.equals("next00"))return cleared(instance,3)?new Travel(STAGE_4,"st00",true,true,""):Travel.deny("Someone must solve all fifteen rows first.");
  }
  if(field==STAGE_4&&name.equals("next00"))return cleared(instance,4)?new Travel(STAGE_5,"st00",true,true,""):Travel.deny("Clear the room and speak to the balloon first.");
  if(field==STAGE_5&&name.equals("next00"))return cleared(instance,5)?new Travel(BOSS,"st00",true,true,""):Travel.deny("Solve the equation and ask the Blue Balloon to check first.");
  return Travel.deny("This portal is not part of the current LPQ route.");
 }
 public static boolean handlePortal(User user,PortalInfo portal){
  var travel=resolvePortal(user,portal);if(travel==null)return false;
  if(!travel.allowed){user.write(WvsContext.scriptProgressMessage("LPQ: "+travel.message));user.dispose();return true;}
  var instance=instance(user).orElseThrow();var destination=instance.getFieldStorage().getFieldById(travel.field).orElseThrow();
  var point=destination.getPortalByName(travel.portal).orElseThrow();
  for(User member:travel.party?members(instance):List.of(user))member.warp(destination,point,false,false);
  if(travel.field>STAGE_2&&travel.field<=STAGE_2+5)user.write(WvsContext.scriptProgressMessage("LPQ: This dark room has "+destination.getMobPool().getCount()+" monsters remaining."));
  return true;
 }
 @Script("stage6_portal")public static void mazeExit(ScriptManager sm){if(sm.getFieldId()==STAGE_3)handlePortal(sm.getUser(),sm.getField().getPortalByName("next00").orElseThrow());}
 public static int[] newQuestion(){
  for(;;){int a=Util.getRandom(20,99),b=Util.getRandom(2,9),c=Util.getRandom(0,30),answer=a*b+c;String digits=String.valueOf(answer);
   if(digits.length()==3&&digits.indexOf('0')<0&&digits.chars().distinct().count()==3)return new int[]{a,b,c,answer};}
 }
 // Two recorded GMS expressions supplement the existing generated questions.
 static String nextEquation(){
  return switch(Util.getRandom(2)){
   case 0 -> "400 + 140 - 72";
   case 1 -> "20 x 30 + 15";
   default -> {
    int[] question=newQuestion();
    yield question[0]+" x "+question[1]+" + "+question[2];
   }
  };
 }
 public static int solveEquation(String equation){
  String[] terms=equation.split(" ");
  if(terms.length!=5)throw new IllegalArgumentException("Unknown LPQ equation: "+equation);
  int a=Integer.parseInt(terms[0]),b=Integer.parseInt(terms[2]),c=Integer.parseInt(terms[4]);
  if(terms[1].equals("x")&&terms[3].equals("+"))return a*b+c;
  if(terms[1].equals("+")&&terms[3].equals("-"))return a+b-c;
  throw new IllegalArgumentException("Unknown LPQ equation: "+equation);
 }
 static void math(ScriptManager sm,Instance instance){
  if(cleared(instance,5)){sm.sayOk("The answer is correct. Enter the door to face Alishar.");return;}
  if(members(instance).stream().anyMatch(user->user.getField()!=sm.getField())){sm.sayNext(PqDialogue.LPQ_MISSING_MEMBERS);return;}
  if(value(instance,"equation").isEmpty()){
   String equation=nextEquation();instance.setVariable("equation",equation);instance.setVariable("math_answer",String.valueOf(solveEquation(equation)));
   sm.getField().blowWeather(RED_SIGN_WEATHER,displayEquation(instance)+" = ?",20);
   sm.sayNext(PqDialogue.LPQ_MATH);sm.sayBoth(PqDialogue.lpqMathHint(displayEquation(instance)));return;
  }
  if(!sm.getUser().isPartyBoss()){sm.sayBoth(PqDialogue.lpqMathHint(displayEquation(instance)));return;}
  String selection=sm.getAreaCheck();if(!matchesDigits(selection,Integer.parseInt(value(instance,"math_answer")))){sm.broadcastScreenEffect("quest/party/wrong_kor");sm.sayOk("The equation is #b"+value(instance,"equation")+"#k. Stand on exactly its three answer digits and try again.");return;}
  clear(instance,5);
 }
 static String displayEquation(Instance instance){return value(instance,"equation").replace(" ","").replace('x','*');}
 public static boolean matchesDigits(String selection,int answer){
  if(selection.length()!=9||selection.chars().filter(c->c=='1').count()!=3)return false;
  String digits=String.valueOf(answer);for(int i=0;i<9;i++)if((selection.charAt(i)=='1')!=digits.contains(String.valueOf(i+1)))return false;return true;
 }
 static void finish(ScriptManager sm,Instance instance){
  if(!value(instance,"boss_defeated").equals("1")){sm.sayOk(PqDialogue.lpqBubble(BOSS));return;}
  if(!instance.getRewards().orElseThrow().hasClaimed(sm.getUser().getCharacterId())){sm.sayOk("Choose your treasure box and receive your reward first.");KerningPQRewards.show(sm.getUser());return;}
  sm.sayNext(PqDialogue.LPQ_FINISHED);
  String key="exit_exp_"+sm.getUser().getCharacterId();
  synchronized(instance){
   if(value(instance,key).isEmpty()){
    instance.setVariable(key,"1");
    sm.addExp(CLEAR_EXP);
   }
  }
  sm.warp(EXIT);
 }
 /** Called only after successful native reward delivery, never merely by closing its window. */
 public static void recordCompletion(User user,Instance instance){
  String key="rewarded_"+user.getCharacterId();if(value(instance,key).equals("1"))return;instance.setVariable(key,"1");
  Map<String,String> record=record(user);record.put("cmp",String.valueOf(number(record,"cmp")+1));record.putIfAbsent("have","0");
  int seconds=(int)(Instant.now().getEpochSecond()-Long.parseLong(value(instance,"started")));int fastest=number(record,"min")*60+number(record,"sec");
  if(fastest==0||seconds<fastest){record.put("min",String.valueOf(seconds/60));record.put("sec",String.valueOf(seconds%60));record.put("date",LocalDate.now().toString());}saveRecord(user,record);
 }
 static Map<String,String> record(User user){
  var result=new LinkedHashMap<String,String>();String text=user.getQuestManager().getQuestRecord(RECORD).map(QuestRecord::getValue).orElse("");
  if(text!=null)for(String part:text.split(";")){String[] pair=part.split("=",2);if(pair.length==2)result.put(pair[0],pair[1]);}return result;
 }
 static int number(Map<String,String> record,String key){String value=record.getOrDefault(key,"0");return value.matches("\\d+")?Integer.parseInt(value):0;}
 public static int recordNumber(User user,String key){return number(record(user),key);}
 static void saveRecord(User user,Map<String,String> record){
  var qr=new QuestRecord(RECORD);qr.setState(QuestState.PARTYQUEST);qr.setValue(record.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining(";")));user.getQuestManager().addQuestRecord(qr);user.write(MessagePacket.questRecordEx(RECORD,qr.getValue()));
 }
 static void claimGlasses(ScriptManager sm){
  var record=record(sm.getUser());int claimed=number(record,"have");if(number(record,"cmp")<(claimed+1)*35){sm.sayOk("Complete Dimensional Crack 35 times per pair. You currently have "+number(record,"cmp")+" clears and have claimed "+claimed+" pair(s).");return;}
  if(!sm.canAddItem(GLASSES,1)||!sm.addItem(GLASSES,1)){sm.sayOk("Make room in your EQUIP inventory, then speak to me again.");return;}
  record.put("have",String.valueOf(claimed+1));saveRecord(sm.getUser(),record);sm.sayOk("Here are your Broken Glasses. Another 35 completions earns another pair.");
 }
 @Script("party2_out")public static void out(ScriptManager sm){
  if(sm.getFieldId()==EXIT){sm.warp(LOBBY,"st00");return;}
  if(instance(sm.getUser()).isEmpty())return;
  if(sm.getFieldId()==STAGE_5){int answer=sm.askMenu("Can I help?",Map.of(0,"Leave this party quest",1,"Buy a Platform Puppet"));if(answer==1){KerningPQ.buyPuppet(sm,Math.max(1,ItemProvider.getItemInfo(PUPPET).orElseThrow().getPrice()));return;}}
  if(sm.askYesNo("Are you sure you want to leave this party quest?"))sm.warp(EXIT);
 }
 private static void update(Instance instance){
  if(value(instance,"boss_defeated").equals("1")||value(instance,"failed").equals("1")||Instant.now().getEpochSecond()<Long.parseLong(value(instance,"ready_after")))return;
  if(members(instance).size()<3){instance.setVariable("failed","1");notice(instance,"LPQ ended because fewer than three living original party members remain.");
   var exit=instance.getChannelServerNode().getFieldById(EXIT).orElseThrow();for(User user:instance.getUsers())user.warp(exit,exit.getRandomStartPoint().orElseThrow(),false,false);instance.getChannelServerNode().removeInstance(instance);}
 }
 @Script("StageMsg_crack")public static void stageMessage(ScriptManager sm){
  String text=PqDialogue.lpqBubble(sm.getFieldId());if(text!=null)sm.getField().blowWeather(RED_SIGN_WEATHER,text,20);
 }
}
