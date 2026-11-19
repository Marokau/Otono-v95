package kinoko.bot;

import kinoko.handler.field.MobHandler;
import kinoko.handler.user.HitHandler;
import kinoko.provider.*;
import kinoko.provider.wz.*;
import kinoko.provider.wz.serialize.*;
import kinoko.server.node.ServerExecutor;
import kinoko.util.Rect;
import kinoko.world.field.Field;
import kinoko.world.field.life.*;
import kinoko.world.field.mob.*;
import kinoko.world.user.User;
import java.util.*;
import java.util.concurrent.*;

/** Native-data PQ encounter simulation when no graphical controller exists, plus hits for headless participants. */
public final class CrewEncounters {
 private record Pattern(Rect area,int delay){}
 private record Data(int speed,Rect body,Map<Integer,Pattern> attacks){}
 private static final Map<Integer,Data> data=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> immuneUntil=new ConcurrentHashMap<>();
 private final Map<Integer,Long> nextAction=new HashMap<>();
 private final Map<Integer,Integer> directions=new HashMap<>();
 private static Data metadata(int id){return data.computeIfAbsent(id,key->{
  try(var wz=WzPackage.from(MobProvider.MOB_WZ)){
   WzProperty root=((WzImage)wz.getItem(String.format("%07d.img",id))).getProperty();
   var info=(WzProperty)root.get("info");
   if(info.get("link")!=null)root=((WzImage)wz.getItem(String.format("%07d.img",WzProvider.getInteger(info.get("link"))))).getProperty();
   int speed=info.get("speed")==null?0:WzProvider.getInteger(info.get("speed"));
   int width=30,height=48;
   if(root.get("stand") instanceof WzProperty stand&&stand.get("0") instanceof WzCanvas canvas&&canvas.getProperty().get("origin") instanceof WzVector origin){width=Math.max(16,origin.getX()*3/4);height=Math.max(24,origin.getY()*4/5);}
   var attacks=new HashMap<Integer,Pattern>();
   for(int index=0;index<10;index++)if(root.get("attack"+(index+1)) instanceof WzProperty attack&&attack.get("info") instanceof WzProperty ai){
    Rect range=null;int delay=900;if(ai.get("range") instanceof WzProperty r && r.get("lt") instanceof WzVector && r.get("rb") instanceof WzVector)range=WzProvider.getRect(r);
    if(ai.get("attackAfter")!=null)delay=WzProvider.getInteger(ai.get("attackAfter"));
    if(range!=null)attacks.put(index,new Pattern(range,delay));
    else if(ai.get("range") instanceof WzProperty r&&r.get("sp") instanceof WzVector center&&r.get("r")!=null){
     int radius=WzProvider.getInteger(r.get("r"));if(radius>0)attacks.put(index+100,new Pattern(new Rect(center.getX()-radius,center.getY()-radius,center.getX()+radius,center.getY()+radius),delay));
    }
   }
   return new Data(speed,new Rect(-width,-height,width,0),Map.copyOf(attacks));
  }catch(Exception e){throw new IllegalStateException("Unable to read native encounter data for "+id,e);}
 });}
 public void tick(Field field,List<User> bots,long now){
  var mobs=new ArrayList<Mob>();field.getMobPool().forEach(mobs::add);
  for(var mob:mobs){
   if(mob.getHp()<=0)continue;
   Data values=metadata(mob.getTemplateId());
   // A real client's controller retains ownership. A headless audit owner cannot drive monsters.
   if((mob.getController()==null||mob.getController().getClient().getChannel() instanceof CrewChannel)&&now>=nextAction.getOrDefault(mob.getId(),0L)){
    nextAction.put(mob.getId(),now+500);
    var attack=new MobAttackInfo();attack.multiTargetForBall=List.of();attack.randTimeForAreaAttack=List.of();
    var skill=mob.getNextSkill();
    boolean usingSkill=skill.isPresent();
    boolean attacking=!usingSkill&&(mob.isBoss())&&ThreadLocalRandom.current().nextInt(12)==0&&mob.getMp()>=2;
    int attackIndex=attacking?mob.getTemplate().getAttacks().keySet().stream().skip(ThreadLocalRandom.current().nextInt(Math.max(1,mob.getTemplate().getAttacks().size()))).findFirst().orElse(0):0;
    attack.actionAndDir=(byte)((usingSkill?MobActionType.SKILL1.getValue():attacking?MobActionType.ATTACK1.getValue()+attackIndex:MobActionType.MOVE.getValue())*2);
    if(usingSkill)attack.targetInfo=skill.get().getSkillId()|(skill.get().getSkillLevel()<<8);
    int x=mob.getX(),y=mob.getY();
    if(!attacking&&!usingSkill){
     int dir=directions.computeIfAbsent(mob.getId(),id->ThreadLocalRandom.current().nextBoolean()?1:-1);
     int nx=x+dir*Math.max(5,(100+values.speed)/4);
     var below=field.getFootholdBelow(nx,y-5);
     if(below.isEmpty()||Math.abs(below.get().getYFromX(nx)-y)>20){directions.put(mob.getId(),-dir);}else{x=nx;y=below.get().getYFromX(nx);}
     if(dir<0)attack.actionAndDir|=1;
    }
    var move=new MoveElem((byte)0);move.setX((short)x);move.setY((short)y);move.setFh((short)field.getFootholdBelow(x,y).map(f->f.getSn()).orElse(0).intValue());
    move.setMoveAction(attack.actionAndDir);move.setElapse((short)500);
    var path=new MovePath((short)mob.getX(),(short)mob.getY(),(short)0,(short)0,List.of(move));
    MobHandler.handleAutomatedMove(mob,attack,path);
   }
   for(User bot:bots)if(bot.getField()==field&&bot.getHp()>0&&now>=immuneUntil.getOrDefault(bot.getCharacterId(),0L)){
    if(values.body.translate(mob.getX(),mob.getY()).isInsideRect(bot.getX(),bot.getY()-10)){
     applyHit(bot,mob,-1);
    }
   }
  }
 }
 static void checkMovingContact(User bot) {
  if (!bot.isAutomated() || !(bot.getField().getFieldStorage() instanceof kinoko.server.field.InstanceFieldStorage)) return;
  if (System.currentTimeMillis() < immuneUntil.getOrDefault(bot.getCharacterId(), 0L)) return;
  bot.getField().getMobPool().getBy(mob -> mob.getHp() > 0 &&
      metadata(mob.getTemplateId()).body.translate(mob.getX(), mob.getY())
          .isInsideRect(bot.getX(), bot.getY() - 10))
      .ifPresent(mob -> applyHit(bot, mob, -1));
 }

 private static void applyHit(User bot,Mob mob,int attack){
  Field field = mob.getField();
  if (bot.getField() != field || mob.getHp() <= 0 ||
      field.getMobPool().getById(mob.getId()).orElse(null) != mob) return;
  long now=System.currentTimeMillis();if(now<immuneUntil.getOrDefault(bot.getCharacterId(),0L))return;immuneUntil.put(bot.getCharacterId(),now+1500);
  int raw=attack>=0&&mob.getTemplate().getAttack(attack).map(a->a.isMagic()).orElse(false)?mob.getTemplate().getMad():mob.getTemplate().getPad();int damage=Math.max(1,(int)(raw*ThreadLocalRandom.current().nextDouble(0.85,1.01)));
  HitHandler.handleAutomatedHit(bot,mob,attack,damage);
  CrewMotion.knockback(bot,bot.getX()<mob.getX()?-1:1);
 }
 public static void observeAttack(Mob mob,MobAttackInfo attack){
  if(!attack.isAttack)return;
  if(mob.getTemplateId()!=9300003)return;
  Data values=metadata(mob.getTemplateId());int index=(attack.actionAndDir>>1)-MobActionType.ATTACK1.getValue();var pattern=values.attacks.getOrDefault(index,values.attacks.get(index+100));if(pattern==null)return;
  boolean left=(attack.actionAndDir&1)!=0;int x=mob.getX(),y=mob.getY();
  var area=(left?pattern.area:pattern.area.flipX()).translate(x,y);
  ServerExecutor.schedule(mob.getField(),()->mob.getField().getUserPool().forEach(user->{
   boolean inside=area.isInsideRect(user.getX(),user.getY()-10);
   if(!metadata(mob.getTemplateId()).attacks.containsKey(index)){long dx=user.getX()-area.getCenterX(),dy=user.getY()-10-area.getCenterY(),radius=(area.getRight()-area.getLeft())/2;inside=dx*dx+dy*dy<=radius*radius;}
   if(user.isAutomated()&&user.getHp()>0&&inside)applyHit(user,mob,index);
  }),pattern.delay,TimeUnit.MILLISECONDS);
 }
}
