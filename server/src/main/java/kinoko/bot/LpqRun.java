package kinoko.bot;

import kinoko.handler.user.PqRewardHandler;
import kinoko.packet.user.*;
import kinoko.packet.world.WvsContext;
import kinoko.script.party.LudibriumPQ;
import kinoko.server.field.*;
import kinoko.server.packet.*;
import kinoko.util.Rect;
import kinoko.world.field.*;
import kinoko.world.field.drop.Drop;
import kinoko.world.field.mob.Mob;
import kinoko.world.field.reactor.Reactor;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterTemporaryStat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Human-led LPQ. All traversal uses native portals or physical movement; the crew knows the fixed box route. */
public final class LpqRun {
 public record Member(User user,KpqCrewPreparation.Role role){}
 private final User leader;private final List<Actor> actors;
 private final CrewEncounters encounters=new CrewEncounters();
 private final Set<Integer> introductions=new HashSet<>(),clears=new HashSet<>();
 static final String BOX_LABELS = "133 221 333 123 111";
 // Portal columns count from the left; the painted digits vary by row.
 private static final String BOX_COLUMNS = "331221112132113";
 private Instance instance;private long startAfter=System.currentTimeMillis()+2000;private boolean mathReady;
 private static final class Actor{
  final User user;final KpqCrewPreparation.Role role;CompletableFuture<Boolean> movement=CompletableFuture.completedFuture(true);
  int fieldId,target=-1,goal=-1,room;long nextAction,nextPotion,ailmentSince;boolean roomEntered;
  Actor(Member member){user=member.user;role=member.role;room=role==KpqCrewPreparation.Role.MAGE?1:role==KpqCrewPreparation.Role.SWORD?4:5;}
 }
 public LpqRun(User leader,List<Member> members){this.leader=leader;actors=members.stream().map(Actor::new).toList();}
 private static String value(Instance i,String key){return i.getVariable(key).orElse("");}
 private static int number(Instance i,String key){String s=value(i,key);return s.matches("\\d+")?Integer.parseInt(s):0;}
 public void update(){
  long now=System.currentTimeMillis();if(now<startAfter)return;
  if(leader.getField().getFieldStorage() instanceof InstanceFieldStorage storage&&value(storage.getInstance(),"lpq").equals("1")&&instance!=storage.getInstance()){
   instance=storage.getInstance();introductions.clear();clears.clear();mathReady=false;
   for(var a:actors){a.fieldId=0;a.room=a.role==KpqCrewPreparation.Role.MAGE?1:a.role==KpqCrewPreparation.Role.SWORD?4:5;a.roomEntered=false;}
  }
  if(instance==null)return;
  var fields=new HashSet<Field>();for(var a:actors)if(a.user.getHp()>0&&a.user.getField().getFieldStorage() instanceof InstanceFieldStorage)fields.add(a.user.getField());
  for(var field:fields)try{encounters.tick(field,actors.stream().map(a->a.user).toList(),now);}catch(Exception error){
   // Recovery and potions must still run if unfamiliar encounter metadata fails.
   if(clears.add(-field.getFieldId()))org.apache.logging.log4j.LogManager.getLogger(LpqRun.class).error("LPQ encounter metadata failed in {}",field.getFieldId(),error);
  }
  for(var a:actors){User u=a.user;if(u.getClient().getUser()!=u||!u.getClient().getChannel().isActive()||u.getHp()<=0)continue;
   if(u.getFieldId()==LudibriumPQ.LOBBY)continue;
   if(a.fieldId!=u.getFieldId()){
    CrewMotion.cancel(u);a.movement=CompletableFuture.completedFuture(true);a.fieldId=u.getFieldId();a.target=-1;a.goal=-1;a.nextAction=now+1800;introduce(a.fieldId);
   }
   if(now>=a.nextPotion&&(u.getHp()<u.getMaxHp()*0.65||u.getMp()<Math.max(30,u.getMaxMp()*0.2))){if(CrewActions.potion(u))a.nextPotion=now+1000;}
   boolean ailment=List.of(CharacterTemporaryStat.Seal,CharacterTemporaryStat.Darkness,CharacterTemporaryStat.Poison,CharacterTemporaryStat.Weakness,CharacterTemporaryStat.Curse).stream().anyMatch(u.getSecondaryStat()::hasOption);
   if(!ailment)a.ailmentSince=0;else if(a.ailmentSince==0)a.ailmentSince=now;else if(now-a.ailmentSince>=3000&&CrewActions.allCure(u)){a.ailmentSince=now;}
   if(u.hasDialog()||now<a.nextAction||u.getSecondaryStat().hasOption(CharacterTemporaryStat.Stun))continue;
   if(a.role==KpqCrewPreparation.Role.MAGE&&!u.getSecondaryStat().hasOption(CharacterTemporaryStat.MagicGuard)&&!u.getSecondaryStat().hasOption(CharacterTemporaryStat.Seal)){CrewActions.buff(u,2001002);a.nextAction=now+900;continue;}
   if(a.movement.isDone()&&CrewMotion.isMoving(u))continue;
   if(!a.movement.isDone()){
    if(a.target>0&&u.getField().getMobPool().getById(a.target).isEmpty()&&u.getField().getReactorPool().getById(a.target).map(Reactor::isLastState).orElse(true)){CrewMotion.cancel(u);a.target=-1;}else continue;
   }
   try{
    switch(u.getFieldId()){
     case LudibriumPQ.STAGE_1->{if(gate("gate1"))waitForLeader(a,now);else fight(a,now);}
     case LudibriumPQ.BOX_STAGE->boxes(a,now);
     case LudibriumPQ.STAGE_2->darkMain(a,now);
     case 922010401,922010402,922010403,922010404,922010405->darkRoom(a,now);
     case LudibriumPQ.STAGE_3->maze(a,now);
     case LudibriumPQ.STAGE_4->{if(gate("gate4"))waitForLeader(a,now);else if(!u.getField().getMobPool().isEmpty())fight(a,now);else if(clears.add(a.fieldId))say(u,"The Rombots are defeated. Please ask the Sky-Blue Balloon to open the door.");}
     case LudibriumPQ.STAGE_5->equation(a,now);
     case LudibriumPQ.BOSS->boss(a,now);
     case LudibriumPQ.EXIT->{if(leader.getFieldId()==LudibriumPQ.LOBBY)talk(a,2040047,now);}
    }
   }catch(Exception e){a.nextAction=now+1500;org.apache.logging.log4j.LogManager.getLogger(LpqRun.class).error("LPQ crew action failed for {}",u.getCharacterName(),e);}
  }
 }
 private void introduce(int id){if(!introductions.add(id))return;String text=switch(id){
  case LudibriumPQ.STAGE_1->"Stage One: We will defeat all the Ratz and Black Ratz. The door opens when none remain. Lead us through when ready.";
  case LudibriumPQ.BOX_STAGE->"Stage Two: We will break the boxes and collect ten passes. Pickups count for the whole party. You can help break boxes too.";
  case LudibriumPQ.STAGE_2->"Stage Three: Our mage will clear the upper three dark rooms; our physical attackers will clear the lower two. We will walk back through each room's exit.";
  case LudibriumPQ.STAGE_3->"Box route: "+BOX_LABELS+". Follow the numbers on the boxes from the bottom up.";
  case LudibriumPQ.STAGE_4->"Stage Five: We will defeat the Rombots and their summons. Afterwards, ask the Sky-Blue Balloon to check.";
  case LudibriumPQ.STAGE_5->"Stage Six: Ask the Blue Balloon for an equation. We will stand on the answer digits, then you can ask it to check. Please stay beside the balloon.";
  case LudibriumPQ.BOSS->"Boss Stage: We will fight Alishar and his summons. All ground loot is yours. Choose your treasure box afterwards, then speak to the Violet Balloon to leave.";
  default->null;};if(text!=null)say(actors.getFirst().user,text);
 }
 private boolean gate(String key){return value(instance,key).equals("1");}
 private void waitForLeader(Actor a,long now){
  if(clears.add(a.fieldId))leader.write(WvsContext.scriptProgressMessage("LPQ: This stage is clear. Lead us through the door when ready."));
  // Stage doors transfer the whole party; bots must not advance a human leader prematurely.
 }
 private void boxes(Actor a,long now){
  if(gate("box_gate")){waitForLeader(a,now);return;}
  var drops=new ArrayList<Drop>();a.user.getField().getDropPool().forEach(d->{if(d.getItem()!=null&&d.getItem().getItemId()==LudibriumPQ.PASS&&d.canPickUp(a.user))drops.add(d);});drops.sort(Comparator.comparingDouble(d->distance(a.user,d.getX(),d.getY())));
  for(var d:drops){if(CrewActions.pickUp(a.user,d)){a.nextAction=now+350;return;}if(go(a,d.getX(),d.getY()))return;}
  var boxes=new ArrayList<Reactor>();a.user.getField().getReactorPool().forEach(r->{if(!r.isLastState()&&actors.stream().noneMatch(other->other!=a&&other.target==r.getId()))boxes.add(r);});boxes.sort(Comparator.comparingDouble(r->distance(a.user,r.getX(),r.getY())));
  for(var box:boxes){if(CrewActions.hitBox(a.user,box)){a.target=box.getId();a.nextAction=now+850;return;}
   if(go(a,box.getX(),box.getY())){a.target=box.getId();return;}}
 }
 private void darkMain(Actor a,long now){
  if(gate("gate2")){waitForLeader(a,now);return;}
  if(a.room>5)return;
  if(a.role!=KpqCrewPreparation.Role.MAGE&&a.roomEntered)return;
  if(a.role==KpqCrewPreparation.Role.MAGE&&a.room>3)return;
  portal(a,"in0"+a.room,now);
 }
 private void darkRoom(Actor a,long now){
  if(!a.user.getField().getMobPool().isEmpty()){fight(a,now);return;}
  if(!a.roomEntered){say(a.user,"My dark room is clear. I am heading back to the door.");a.roomEntered=true;}
  if(portal(a,"out00",now)&&a.role==KpqCrewPreparation.Role.MAGE){a.room++;a.roomEntered=false;}
 }
 private void maze(Actor a,long now){
  if(gate("gate3")){waitForLeader(a,now);return;}
  int row=number(instance,"maze_progress_"+a.user.getCharacterId());
  portal(a,mazePortal(row),now);
 }
 static String mazePortal(int row){
  int column=BOX_COLUMNS.charAt(row)-'1';
  return String.format("h%03d",row*3+column);
 }
 public static int solveEquation(String equation){
  return LudibriumPQ.solveEquation(equation);
 }
 private void equation(Actor a,long now){
  if(gate("gate5")){waitForLeader(a,now);return;}
  String question=value(instance,"equation");if(question.isEmpty())return;
  String digits=String.valueOf(solveEquation(question));int index=actors.indexOf(a);Rect area=a.user.getField().getMapInfo().getAreas().get(digits.charAt(index)-'1');
  if(!area.isInsideRect(a.user.getX(),a.user.getY())){
   var graph=KpqRun.navigation(a.user);int node=best(graph,p->p.motion()==CrewNavigation.Motion.WALK&&area.isInsideRect(p.x(),p.y()),area.getCenterX(),area.getCenterY());if(node>=0)move(a,node);
  }
  if(!mathReady){boolean ready=true;for(int i=0;i<actors.size();i++)if(actors.get(i).user.getField()!=a.user.getField()||!a.user.getField().getMapInfo().getAreas().get(digits.charAt(i)-'1').isInsideRect(actors.get(i).user.getX(),actors.get(i).user.getY()))ready=false;
   if(ready){mathReady=true;say(a.user,"The answer is "+digits+". We are on its digits; please ask the Blue Balloon to check.");}}
 }
 private void boss(Actor a,long now){
  if(!gate("boss_defeated")){fight(a,now);return;}a.target=-1;var rewards=instance.getRewards().orElseThrow();
  if(rewards.selectedBox(a.user.getCharacterId())<0){for(int box=0;box<6;box++)if(!rewards.isTaken(box)){PqRewardHandler.select(a.user,new NioBufferInPacket(new byte[]{(byte)box}));break;}}
  else if(!rewards.hasClaimed(a.user.getCharacterId())){PqRewardHandler.claim(a.user,new NioBufferInPacket(new byte[0]));a.nextAction=now+5000;}
  else if(leader.getFieldId()==LudibriumPQ.EXIT||leader.getFieldId()==LudibriumPQ.LOBBY)talk(a,2040044,now);
 }
 private void fight(Actor a,long now){
  var mobs=new ArrayList<Mob>();a.user.getField().getMobPool().forEach(m->{if(m.getHp()>0)mobs.add(m);});mobs.sort(Comparator.comparingDouble(m->distance(a.user,m.getX(),m.getY())));
  for(var mob:mobs){if(CrewActions.attack(a.user,mob,a.role)){a.target=mob.getId();a.nextAction=now+1000;return;}
   var graph=KpqRun.navigation(a.user);int range=a.role==KpqCrewPreparation.Role.SWORD?65:190;
   int node=best(graph,p->p.motion()==CrewNavigation.Motion.WALK&&Math.abs(p.y()-mob.getY())<=35&&Math.abs(p.x()-mob.getX())<=range&&Math.abs(p.x()-mob.getX())>=35,a.user.getX(),a.user.getY());
   if(node<0)node=graph.groundNode(mob.getX(),mob.getY()).orElse(-1);if(node>=0&&move(a,node)){a.target=mob.getId();return;}
  }
 }
 private boolean portal(Actor a,String name,long now){var p=a.user.getField().getPortalByName(name).orElseThrow();if(CrewActions.portal(a.user,name)){a.nextAction=now+1000;a.target=-1;return true;}go(a,p.getX(),p.getY());return false;}
 private void talk(Actor a,int npc,long now){if(CrewActions.talk(a.user,npc)){a.nextAction=now+1000;return;}int node=KpqRun.npcApproach(a.user,npc);if(node>=0)move(a,node);}
 private static double distance(User u,int x,int y){return Math.abs(u.getX()-x)+Math.abs(u.getY()-y)*1.4;}
 private static int best(CrewNavigation g,Predicate<CrewNavigation.Point> filter,int x,int y){int result=-1;double score=Double.MAX_VALUE;for(int i=0;i<g.points().size();i++){var p=g.points().get(i);if(filter.test(p)){double d=Math.abs(p.x()-x)+Math.abs(p.y()-y)*1.4;if(d<score){score=d;result=i;}}}return result;}
 private boolean go(Actor a,int x,int y){int node=KpqRun.navigation(a.user).groundNode(x,y).orElse(-1);return node>=0&&move(a,node);}
 private boolean move(Actor a,int node){if(!a.movement.isDone())return true;var graph=KpqRun.navigation(a.user);var p=graph.points().get(node);if(a.user.getX()==p.x()&&a.user.getY()==p.y())return false;a.goal=node;a.movement=CrewMotion.moveTo(a.user,graph,node);
  var movement=a.movement;
  if(!movement.isDone())movement.whenComplete((success,error)->{
   if(error==null&&Boolean.TRUE.equals(success))kinoko.server.node.ServerExecutor.submit(a.user,()->{
    if(a.movement==movement&&leader.getClient().getUser()==leader&&leader.getClient().getChannel().isActive()&&a.user.getHp()>0&&a.user.getPartyId()>0&&a.user.getPartyId()==leader.getPartyId()&&a.user.getField().getFieldStorage()==leader.getField().getFieldStorage())update();
   });
  });
  return !a.movement.isDone()||a.movement.getNow(false);}
 private static void say(User u,String text){u.getField().broadcastPacket(UserPacket.userChat(u,ChatType.NORMAL,text,false));}
 public String status(){return actors.stream().map(a->a.user.getCharacterName()+" map="+a.user.getFieldId()+" pos="+a.user.getX()+","+a.user.getY()+" hp="+a.user.getHp()+" mp="+a.user.getMp()+" target="+a.target+" moving="+!a.movement.isDone()+" goal="+a.goal+" room="+a.room).reduce((a,b)->a+"\n"+b).orElse("");}
}
