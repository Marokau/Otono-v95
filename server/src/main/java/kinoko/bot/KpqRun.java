package kinoko.bot;

import kinoko.handler.user.PqRewardHandler;
import kinoko.packet.user.*;
import kinoko.packet.world.WvsContext;
import kinoko.provider.MapProvider;
import kinoko.script.party.KerningPQ;
import kinoko.server.field.*;
import kinoko.server.packet.*;
import kinoko.util.Rect;
import kinoko.world.field.*;
import kinoko.world.field.drop.Drop;
import kinoko.world.field.mob.Mob;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterTemporaryStat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Human-led KPQ state machine. Puzzle answers are tried through normal leader checks, never read. */
public final class KpqRun {
 public record Member(User user,KpqCrewPreparation.Role role){}
 private static final Map<String,CrewNavigation> navigation=new ConcurrentHashMap<>();
 private final User leader;
 private final List<Actor> actors;
 private final CrewEncounters encounters=new CrewEncounters();
 private final Map<Integer,Integer> announcedArrangement=new HashMap<>();
 private final Set<Integer> introductions=new HashSet<>(),clears=new HashSet<>();
 private long startAfter=System.currentTimeMillis()+2000;
 private Instance instance;
 private static final class Actor {
  final User user;final KpqCrewPreparation.Role role;
  CompletableFuture<Boolean> movement=CompletableFuture.completedFuture(true);
  int fieldId,target=-1,couponTarget=-1,goal=-1,lastCount=-1;long nextAction,nextPotion;boolean delivered;
  Actor(Member member){user=member.user;role=member.role;}
 }
 public KpqRun(User leader,List<Member> members){this.leader=leader;actors=members.stream().map(Actor::new).toList();}
 public static CrewNavigation navigation(User user){
  int speed=user.getSecondaryStat().getSpeed(),jump=user.getSecondaryStat().getJump();
  String key=user.getFieldId()+":"+speed+":"+jump;
  return navigation.computeIfAbsent(key,k->new CrewNavigation(user.getField().getMapInfo(),MapProvider.getPhysicsConstants(),speed,jump));
 }
 private static String variable(Instance instance,String key){return instance.getVariable(key).orElse("");}
 private static int number(Instance instance,String key){String value=variable(instance,key);return value.matches("\\d+")?Integer.parseInt(value):0;}
 public void update(){
  long now=System.currentTimeMillis();if(now<startAfter)return;
  if(leader.getField().getFieldStorage() instanceof InstanceFieldStorage storage&&variable(storage.getInstance(),"kpq").equals("1")&&instance!=storage.getInstance()) {
   instance=storage.getInstance();introductions.clear();clears.clear();announcedArrangement.clear();
   for(var actor:actors){actor.fieldId=0;actor.target=-1;actor.couponTarget=-1;actor.goal=-1;}
  }
  if(instance==null)return;
  var fields=new HashSet<Field>();for(var actor:actors)if(actor.user.getHp()>0)fields.add(actor.user.getField());
  for(var field:fields)if(field.getFieldStorage() instanceof InstanceFieldStorage)encounters.tick(field,actors.stream().map(a->a.user).toList(),now);
  for(var actor:actors){
   User user=actor.user;if(user.getClient().getUser()!=user||!user.getClient().getChannel().isActive()||user.getHp()<=0)continue;
   if(user.getFieldId()==KerningPQ.LOBBY)continue;
   if(actor.fieldId!=user.getFieldId()){
    CrewMotion.cancel(user);actor.movement=CompletableFuture.completedFuture(true);actor.fieldId=user.getFieldId();actor.target=-1;actor.couponTarget=-1;actor.goal=-1;actor.lastCount=-1;actor.delivered=false;actor.nextAction=now+1800;
    introduce(user.getFieldId());
   }
   if(now>=actor.nextPotion&&(user.getHp()<user.getMaxHp()*0.65||user.getMp()<Math.max(30,user.getMaxMp()*0.20))){if(CrewActions.potion(user))actor.nextPotion=now+1000;}
   if(user.getJob()==200&&now>=actor.nextAction&&!user.getSecondaryStat().hasOption(CharacterTemporaryStat.MagicGuard)&&!user.hasDialog()){
    CrewActions.buff(user,2001002);actor.nextAction=now+900;continue;
   }
   if(user.hasDialog()||now<actor.nextAction||user.getSecondaryStat().hasOption(CharacterTemporaryStat.Stun))continue;
   if(actor.movement.isDone() && CrewMotion.isMoving(user))continue;
   if(!actor.movement.isDone()){
    boolean mobGone = actor.target > 0 && user.getField().getMobPool().getById(actor.target).isEmpty();
    boolean couponGone = actor.couponTarget > 0 && user.getField().getDropPool().getById(actor.couponTarget).isEmpty();
    if (mobGone || couponGone) {
     CrewMotion.cancel(user);
     actor.target = -1;
     actor.couponTarget = -1;
    }
    else continue;
   }
   try{
    switch(user.getFieldId()){
     case KerningPQ.STAGE_1 -> firstStage(actor,now);
     case KerningPQ.STAGE_2 -> puzzle(actor,2,now);
     case KerningPQ.STAGE_3 -> puzzle(actor,3,now);
     case KerningPQ.STAGE_4 -> combatStage(actor,4,9300002,now);
     case KerningPQ.STAGE_5 -> finalStage(actor,now);
     case KerningPQ.EXIT -> {if(leader.getFieldId()==KerningPQ.LOBBY)talk(actor,9020002,now);}
    }
   }catch(Exception e){actor.nextAction=now+1500;org.apache.logging.log4j.LogManager.getLogger(KpqRun.class).error("KPQ crew action failed for {}",user.getCharacterName(),e);}
  }
 }
 private void introduce(int field){
  if(!introductions.add(field))return;
  String text=switch(field){
   case KerningPQ.STAGE_1 -> "Stage One: We'll ask Cloto for coupon missions, defeat Ligators and bring you our yellow passes. Pick them up and ask Cloto to check.";
   case KerningPQ.STAGE_2 -> "Stage Two: We'll try three ropes at a time. Stay beside Cloto and ask her to check when we're in position.";
   case KerningPQ.STAGE_3 -> "Stage Three: We'll try three kitten platforms at a time. Ask Cloto to check each arrangement when we're ready.";
   case KerningPQ.STAGE_4 -> "Stage Four: We'll defeat every Curse Eye. Passes aren't needed here; the portal opens when the room is clear.";
   case KerningPQ.STAGE_5 -> "Final Stage: We'll defeat King Slime below. All ground loot is yours. Afterwards, choose a treasure box and speak to Cloto to leave.";
   default -> null;
  };if(text!=null)say(actors.getFirst().user,text);
 }
 private void firstStage(Actor actor,long now){
  User user=actor.user;if(gate(1)){followPortal(actor,1,now);return;}
  String mission=variable(instance,"coupon_mission_"+user.getCharacterId());
  if(mission.equals("clear")){
   if(user.getInventoryManager().getItemCount(KerningPQ.PASS)>0){
    if(user.getField()==leader.getField()&&Math.abs(user.getX()-leader.getX())<=24&&Math.abs(user.getY()-leader.getY())<=25){
     if(CrewActions.drop(user,KerningPQ.PASS,1)){say(user,"Here's my yellow pass. Please pick it up.");actor.delivered=true;actor.nextAction=now+1000;}
    }else go(actor,leader.getX(),leader.getY());
   }else if(actors.stream().allMatch(a->variable(instance,"coupon_mission_"+a.user.getCharacterId()).equals("clear"))&&clears.add(KerningPQ.STAGE_1)){
    leader.write(WvsContext.scriptProgressMessage("Our coupon missions are complete. Collect all three yellow passes and ask Cloto to check."));
   }
   return;
  }
  if(mission.isEmpty()){talk(actor,9020001,now);return;}
  int required=Integer.parseInt(mission),count=user.getInventoryManager().getItemCount(KerningPQ.COUPON);
  if(actor.lastCount<0){say(user,"Cloto asked me for "+required+" coupons. I'll collect them now.");actor.lastCount=count;}
  if(count>required){CrewActions.drop(user,KerningPQ.COUPON,count-required);actor.nextAction=now+500;return;}
  if(count==required){talk(actor,9020001,now);return;}
  if(count/5>actor.lastCount/5){say(user,"I need "+(required-count)+" more coupons for my pass.");actor.lastCount=count;}
  var drops=new ArrayList<Drop>();user.getField().getDropPool().forEach(drop->{if(drop.getItem()!=null&&drop.getItem().getItemId()==KerningPQ.COUPON&&drop.canPickUp(user)&&!reservedForFinisher(actor,drop,now))drops.add(drop);});
  drops.sort(Comparator.comparingDouble(drop->distance(user,drop.getX(),drop.getY())));
  if (actor.role == KpqCrewPreparation.Role.MAGE &&
      drops.stream().noneMatch(drop -> Math.abs(user.getX() - drop.getX()) <= 30 && Math.abs(user.getY() - drop.getY()) <= 35)) {
   var nearby = new ArrayList<Mob>();
   user.getField().getMobPool().forEach(mob -> {
    if (mob.getTemplateId() == 9300001 && mob.getHp() > 0) nearby.add(mob);
   });
   nearby.sort(Comparator.comparingDouble(mob -> distance(user, mob.getX(), mob.getY())));
   for (var mob : nearby) {
    if (CrewActions.attack(user, mob, actor.role)) {
     actor.target = mob.getId();
     actor.nextAction = now + 1000;
     return;
    }
   }
  }
  actor.couponTarget = -1;
  for (Drop drop : drops) {
   if (actors.stream().anyMatch(other -> other != actor && other.user.getField() == user.getField()
       && other.couponTarget == drop.getId() && !other.movement.isDone())) continue;
   // The previous attack's target no longer owns the route to this drop.
   actor.target = -1;
   if (CrewActions.pickUp(user, drop)) {
    actor.nextAction = now + 250;
    return;
   }
   if (go(actor, drop.getX(), drop.getY())) {
    actor.couponTarget = drop.getId();
    return;
   }
  }
  fight(actor,9300001,now);
 }
 private boolean reservedForFinisher(Actor actor, Drop drop, long now) {
  if (now - drop.getCreateTime().toEpochMilli() >= 5000 || !(drop.getSource() instanceof Mob mob)) return false;
  for (Actor other : actors) {
   if (other == actor || other.user.getCharacterId() != mob.getFinishingAttackerId() ||
       other.user.getField() != actor.user.getField() || other.user.getHp() <= 0 || !drop.canPickUp(other.user)) continue;
   int required = number(instance, "coupon_mission_" + other.user.getCharacterId());
   return required > other.user.getInventoryManager().getItemCount(KerningPQ.COUPON);
  }
  return false;
 }

 private boolean gate(int stage){return variable(instance,"stage"+stage+"_gate").equals("1");}
 private void followPortal(Actor actor,int stage,long now){
  if(clears.add(actor.fieldId)&&stage<4)leader.write(WvsContext.scriptProgressMessage("Stage "+stage+" is clear. Lead us through the portal when ready."));
  if(leader.getField()==actor.user.getField())return;
  var portal=actor.user.getField().getPortalByName("next000").orElseThrow();
  if(CrewActions.portal(actor.user,portal.getPortalName())){actor.nextAction=now+1000;return;}
  go(actor,portal.getX(),portal.getY());
 }
 public static List<int[]> combinations(int areas){
  var result=new ArrayList<int[]>();for(int a=0;a<areas;a++)for(int b=a+1;b<areas;b++)for(int c=b+1;c<areas;c++)result.add(new int[]{a,b,c});return result;
 }
 private void puzzle(Actor actor,int stage,long now){
  if(gate(stage)){followPortal(actor,stage,now);return;}
  var areas=actor.user.getField().getMapInfo().getAreas();var options=combinations(areas.size());int attempt=number(instance,"stage"+stage+"_attempts");int[] arrangement=options.get(attempt%options.size());
  int index=actors.indexOf(actor);Rect area=areas.get(arrangement[index]);
  CrewNavigation graph=navigation(actor.user);
  int target=best(graph,point->area.isInsideRect(point.x(),point.y())&&(stage==2?point.motion()==CrewNavigation.Motion.ROPE:point.motion()==CrewNavigation.Motion.WALK),area.getCenterX(),area.getCenterY());
  if(target<0)throw new IllegalStateException("No physical point in puzzle area "+arrangement[index]);
  if(!area.isInsideRect(actor.user.getX(),actor.user.getY())||Math.max(0,actor.user.getFoothold())!=graph.points().get(target).foothold())move(actor,target);
  boolean ready=true;
  for(int i=0;i<actors.size();i++){
   var bot=actors.get(i);if(bot.user.getField()!=actor.user.getField()||!bot.movement.isDone()||!areas.get(arrangement[i]).isInsideRect(bot.user.getX(),bot.user.getY()))ready=false;
  }
  if(ready&&announcedArrangement.getOrDefault(stage,-1)!=attempt){
   announcedArrangement.put(stage,attempt);say(actors.getFirst().user,(stage==2?"Rope":"Platform")+" arrangement "+(attempt+1)+" is ready. Please ask Cloto to check.");
  }
 }
 private void combatStage(Actor actor,int stage,int monster,long now){if(gate(stage)){followPortal(actor,stage,now);return;}fight(actor,monster,now);}
 private void fight(Actor actor,int template,long now){
  var mobs=new ArrayList<Mob>();actor.user.getField().getMobPool().forEach(mob->{if(mob.getTemplateId()==template&&mob.getHp()>0)mobs.add(mob);});
  mobs.sort(Comparator.comparingDouble(mob->distance(actor.user,mob.getX(),mob.getY())));
  for(var target:mobs){
   if(CrewActions.attack(actor.user,target,actor.role)){actor.target=target.getId();actor.nextAction=now+1000;return;}
   var graph=navigation(actor.user);int range=actor.role==KpqCrewPreparation.Role.SWORD?65:190;
   boolean magicClaw = actor.role == KpqCrewPreparation.Role.MAGE && !actor.user.getSecondaryStat().hasOption(CharacterTemporaryStat.Seal);
   int point=best(graph,p->p.motion()==CrewNavigation.Motion.WALK && (magicClaw ? CrewActions.canCastMagicClaw(actor.user, target, p.x(), p.y()) : Math.abs(p.y()-target.getY())<=35 && Math.abs(p.x()-target.getX())<=range && Math.abs(p.x()-target.getX())>=35),actor.user.getX(),actor.user.getY());
   if(point<0)point=graph.groundNode(target.getX(),target.getY()).orElse(-1);
   if(point>=0&&move(actor,point)){actor.target=target.getId();return;}
  }
 }
 private void finalStage(Actor actor,long now){
  if(!variable(instance,"boss_defeated").equals("1")){fight(actor,9300003,now);return;}
  actor.target=-1;var rewards=instance.getRewards().orElseThrow();
  if(rewards.selectedBox(actor.user.getCharacterId())<0){
   for(int box=0;box<6;box++)if(!rewards.isTaken(box)){var p=OutPacket.of();p.encodeByte(box);PqRewardHandler.select(actor.user,new NioBufferInPacket(p.getData()));break;}
  }else if(!rewards.hasClaimed(actor.user.getCharacterId())){PqRewardHandler.claim(actor.user,new NioBufferInPacket(new byte[0]));actor.nextAction=now+5000;}
  else if(leader.getFieldId()==KerningPQ.EXIT||leader.getFieldId()==KerningPQ.LOBBY)talk(actor,9020001,now);
 }
 private void talk(Actor actor,int template,long now){
  var npc=actor.user.getField().getNpcPool().getByTemplateId(template).orElseThrow();
  if(CrewActions.talk(actor.user,template)){actor.nextAction=now+1000;return;}
  int approach=npcApproach(actor.user,template);if(approach>=0)move(actor,approach);
 }
 public static int npcApproach(User user,int template){
  var npc=user.getField().getNpcPool().getByTemplateId(template).orElseThrow();var graph=navigation(user);
  return best(graph,point->point.motion()==CrewNavigation.Motion.WALK&&Math.abs(point.x()-npc.getX())<=380&&Math.abs(point.y()-npc.getY())<=280,user.getX(),user.getY());
 } private static double distance(User user,int x,int y){return Math.abs(user.getX()-x)+Math.abs(user.getY()-y)*1.4;}
 private static int best(CrewNavigation graph,Predicate<CrewNavigation.Point> filter,int x,int y){
  int result=-1;double score=Double.MAX_VALUE;
  for(int i=0;i<graph.points().size();i++){var point=graph.points().get(i);if(!filter.test(point))continue;double value=Math.abs(point.x()-x)+Math.abs(point.y()-y)*1.4;if(value<score){score=value;result=i;}}
  return result;
 }
 private boolean go(Actor actor,int x,int y){int node=navigation(actor.user).groundNode(x,y).orElse(-1);return node>=0&&move(actor,node);}
 private boolean move(Actor actor,int node){
  if(!actor.movement.isDone())return true;
  var graph=navigation(actor.user);var point=graph.points().get(node);
  if(actor.user.getX()==point.x()&&actor.user.getY()==point.y())return false;
  actor.goal=node;actor.movement=CrewMotion.moveTo(actor.user,graph,node);
  var movement=actor.movement;
  if(!movement.isDone())movement.whenComplete((success,error)->{
   if(error==null&&Boolean.TRUE.equals(success))kinoko.server.node.ServerExecutor.submit(actor.user,()->{
    if(actor.movement==movement&&leader.getClient().getUser()==leader&&leader.getClient().getChannel().isActive()&&actor.user.getHp()>0&&actor.user.getPartyId()>0&&actor.user.getPartyId()==leader.getPartyId()&&actor.user.getField().getFieldStorage()==leader.getField().getFieldStorage())update();
   });
  });
  return !actor.movement.isDone()||actor.movement.getNow(false);
 }
 private static void say(User user,String text){user.getField().broadcastPacket(UserPacket.userChat(user,ChatType.NORMAL,text,false));}
 public String status(){return actors.stream().map(a->a.user.getCharacterName()+" map="+a.user.getFieldId()+" pos="+a.user.getX()+","+a.user.getY()+" HP="+a.user.getHp()+" coupons="+a.user.getInventoryManager().getItemCount(KerningPQ.COUPON)+" passes="+a.user.getInventoryManager().getItemCount(KerningPQ.PASS)+" moving="+!a.movement.isDone()+" goal="+a.goal).reduce((a,b)->a+"\n"+b).orElse("");}
}
