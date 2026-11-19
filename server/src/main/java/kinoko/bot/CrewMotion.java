package kinoko.bot;

import kinoko.packet.user.UserRemote;
import kinoko.provider.MapProvider;
import kinoko.server.node.ServerExecutor;
import kinoko.world.field.Field;
import kinoko.world.field.life.MoveElem;
import kinoko.world.field.life.MovePath;
import kinoko.world.user.User;
import java.util.*;
import java.util.concurrent.*;

/** Executes a precomputed physical route at its real duration, using the native remote movement protocol. */
public final class CrewMotion {
    private static final Map<Integer,Runner> active=new ConcurrentHashMap<>();
    private CrewMotion() {}
    public static void cancel(User user) {
        Runner prior=active.remove(user.getCharacterId());if(prior!=null&&!prior.result.isDone()){prior.stopAtCurrent();prior.result.complete(false);}
    }
    public static boolean isMoving(User user) { return active.containsKey(user.getCharacterId()); }
    public static void knockback(User user,int direction) {
        cancel(user);if(user.getHp()<=0)return;
        var field=user.getField();var physics=MapProvider.getPhysicsConstants();var frames=new ArrayList<CrewNavigation.Frame>();
        double x=user.getX(),y=user.getY(),vy=-250,vx=Integer.signum(direction)*150;
        for(int i=0;i<100;i++) {
            double dt=CrewNavigation.FRAME_MS/1000.0;
            double nx=x+vx*dt,ny=y+vy*dt+0.5*physics.getGravityAcc()*dt*dt;
            nx=Math.clamp(nx,field.getMapInfo().getRootBounds().getLeft()+1,field.getMapInfo().getRootBounds().getRight()-1);
            for(var wall:field.getMapInfo().getFootholds())if(wall.isWall()&&wall.isIntersect((int)x,(int)y,(int)nx,(int)ny)){nx=x;vx=0;break;}
            vy=Math.min(physics.getFallSpeed(),vy+physics.getGravityAcc()*dt);
            int foothold = 0;
            if (ny > y) {
                double firstContact = Double.POSITIVE_INFINITY;
                for (var floor : field.getMapInfo().getFootholds()) {
                    if (floor.isWall() || floor.getX2() <= floor.getX1()) continue;
                    double slope = (floor.getY2() - floor.getY1()) / (double)(floor.getX2() - floor.getX1());
                    double relativeDrop = ny - y - slope * (nx - x);
                    if (relativeDrop <= 0) continue;
                    double contact = (floor.getY1() + slope * (x - floor.getX1()) - y) / relativeDrop;
                    double contactX = x + contact * (nx - x);
                    if (contact >= 0 && contact <= 1 && contact < firstContact &&
                        contactX >= floor.getX1() && contactX <= floor.getX2()) {
                        firstContact = contact;
                        foothold = floor.getSn();
                    }
                }
                if (foothold != 0) {
                    nx = x + firstContact * (nx - x);
                    ny = y + firstContact * (ny - y);
                    vy = 0;
                }
            }
            var point=new CrewNavigation.Point((int)Math.round(nx),(int)Math.round(ny),foothold,foothold==0?CrewNavigation.Motion.AIR:CrewNavigation.Motion.WALK);
            frames.add(new CrewNavigation.Frame(point,(int)vx,(int)vy,CrewNavigation.FRAME_MS));x=nx;y=ny;
            if(foothold!=0)break;
        }
        var result=new CompletableFuture<Boolean>();var runner=new Runner(user,field,frames,result);runner.forced=true;
        active.put(user.getCharacterId(),runner);result.whenComplete((success,error)->active.remove(user.getCharacterId(),runner));runner.step();
    }    public static CompletableFuture<Boolean> moveTo(User user,CrewNavigation graph,int target) {
        if(!user.isAutomated() && !(user.getClient().getChannel() instanceof CrewChannel))throw new IllegalArgumentException("Only dedicated automated characters may use crew movement");
        cancel(user);
        var result=new CompletableFuture<Boolean>();
        var field=user.getField();var frames=new ArrayList<CrewNavigation.Frame>();
        int rope=-1;
        for(int i=0;i<graph.points().size();i++){
            var point=graph.points().get(i);
            if((point.motion()==CrewNavigation.Motion.ROPE||point.motion()==CrewNavigation.Motion.LADDER)&&point.x()==user.getX()&&Math.abs(point.y()-user.getY())<=1 && user.getFoothold()<=0){rope=i;break;}
        }
        var physics=MapProvider.getPhysicsConstants();int from=rope;
        if(from<0){
            var surface = field.getMapInfo().getFootholds().stream()
                    .filter(fh -> fh.getSn() == user.getFoothold() && !fh.isWall())
                    .filter(fh -> user.getX() >= fh.getX1() && user.getX() <= fh.getX2())
                    .filter(fh -> fh.getYFromX(user.getX()) == user.getY())
                    .findFirst()
                    .or(() -> field.getFootholdBelow(user.getX(), user.getY()));
            if(surface.isEmpty()){result.complete(false);return result;}
            var fh=surface.get();int floor=fh.getYFromX(user.getX());
            double y=user.getY(),vy=0;
            for(int i=0;y<floor&&i<100;i++){
                double dt=CrewNavigation.FRAME_MS/1000.0;
                double next=Math.min(floor,y+vy*dt+0.5*physics.getGravityAcc()*dt*dt);
                vy=Math.min(physics.getFallSpeed(),vy+physics.getGravityAcc()*dt);y=next;
                var point=new CrewNavigation.Point(user.getX(),(int)Math.round(y),y==floor?fh.getSn():0,y==floor?CrewNavigation.Motion.WALK:CrewNavigation.Motion.AIR);
                frames.add(new CrewNavigation.Frame(point,0,(int)vy,CrewNavigation.FRAME_MS));
            }
            from=graph.groundNodeOnFoothold(user.getX(),fh.getSn()).orElse(-1);
            if(from<0){result.complete(false);return result;}
            var start=graph.points().get(from);
            if(start.foothold()!=fh.getSn()){result.complete(false);return result;}
            int x=user.getX();double speed=graph.walkSpeed();
            while(x!=start.x()){
                int change=(int)Math.copySign(Math.min(Math.abs(start.x()-x),Math.max(1,speed*CrewNavigation.FRAME_MS/1000.0)),start.x()-x);
                x+=change;
                frames.add(new CrewNavigation.Frame(new CrewNavigation.Point(x,fh.getYFromX(x),fh.getSn(),CrewNavigation.Motion.WALK),Integer.signum(change)*(int)speed,0,CrewNavigation.FRAME_MS));
            }
        }
        var route=graph.route(from,target);
        if(from!=target&&route.isEmpty()){result.complete(false);return result;}
        route.forEach(edge->frames.addAll(edge.frames()));
        var runner=new Runner(user,field,frames,result);active.put(user.getCharacterId(),runner);
        result.whenComplete((success,error)->active.remove(user.getCharacterId(),runner));
        runner.step();return result;
    }
    private static final class Runner {
        final User user;final Field field;final List<CrewNavigation.Frame> frames;final CompletableFuture<Boolean> result;
        int index,publishedUntil;int expectedX,expectedY;boolean forced,paused;
        final long startedNanos=System.nanoTime();final long[] frameEnds;long pausedSinceNanos,pausedNanos;
        Runner(User user,Field field,List<CrewNavigation.Frame> frames,CompletableFuture<Boolean> result){this.user=user;this.field=field;this.frames=frames;this.result=result;expectedX=user.getX();expectedY=user.getY();frameEnds=new long[frames.size()];long elapsed=0;for(int n=0;n<frames.size();n++){elapsed+=TimeUnit.MILLISECONDS.toNanos(frames.get(n).milliseconds());frameEnds[n]=elapsed;}}
        MoveElem element(CrewNavigation.Frame frame,boolean left){
            var point=frame.point();var elem=new MoveElem((byte)(point.motion()==CrewNavigation.Motion.DROP?12:0));
            elem.setX((short)point.x());elem.setY((short)point.y());elem.setFh((short)((point.motion()==CrewNavigation.Motion.ROPE||point.motion()==CrewNavigation.Motion.LADDER)?-1:point.foothold()));
            elem.setFhFallStart((short)user.getFoothold());elem.setVx((short)frame.vx());elem.setVy((short)frame.vy());
            int action=switch(point.motion()){case WALK->frame.vx()==0?4:2;case AIR,DROP->6;case LADDER->14;case ROPE->16;};
            elem.setMoveAction((byte)(action|(left?1:0)));elem.setElapse((short)frame.milliseconds());return elem;
        }
        void stopAtCurrent(){
            if(user.getField()!=field||user.getHp()<=0||!user.getClient().getChannel().isActive())return;
            var stop=new MoveElem((byte)0);stop.setX((short)user.getX());stop.setY((short)user.getY());stop.setFh((short)user.getFoothold());
            int state=user.getFoothold()>0?4:6;
            if(user.getFoothold()<0&&!frames.isEmpty())state=switch(frames.get(Math.max(0,index-1)).point().motion()){case ROPE->16;case LADDER->14;default->6;};
            stop.setMoveAction((byte)(state|(user.isLeft()?1:0)));stop.setElapse((short)0);
            var path=new MovePath((short)user.getX(),(short)user.getY(),(short)0,(short)0,List.of(stop));path.applyTo(user);field.broadcastPacket(UserRemote.move(user,path),user);
        }
        void publishWindow(){
            var elements=new ArrayList<MoveElem>();int duration=0,x=user.getX(),y=user.getY();boolean left=user.isLeft();publishedUntil=index;
            while(publishedUntil<frames.size()&&duration<400){
                var frame=frames.get(publishedUntil++);duration+=frame.milliseconds();
                if(!forced&&frame.vx()!=0)left=frame.vx()<0;
                // Zero-length graph joins update server footholds but must not create an idle animation.
                boolean junction=frame.milliseconds()<=1&&frame.vx()==0&&frame.vy()==0&&frame.point().x()==x&&frame.point().y()==y;
                if(junction){if(!elements.isEmpty())elements.getLast().setElapse((short)(elements.getLast().getElapse()+frame.milliseconds()));}
                else elements.add(element(frame,left));
                x=frame.point().x();y=frame.point().y();
            }
            if(!elements.isEmpty()){
                var previous=index==0?null:frames.get(index-1);
                field.broadcastPacket(UserRemote.move(user,new MovePath((short)user.getX(),(short)user.getY(),(short)(previous==null?0:previous.vx()),(short)(previous==null?0:previous.vy()),elements)),user);
            }
        }
        void step(){
            try {
                if(result.isDone())return;
                if(user.getX()!=expectedX||user.getY()!=expectedY){result.complete(false);return;}
                if(user.getClient().getUser()!=user||!user.getClient().getChannel().isActive()||user.getField()!=field||user.getHp()<=0){result.complete(false);return;}
                if(!forced&&user.getSecondaryStat().hasOption(kinoko.world.user.stat.CharacterTemporaryStat.Stun)){
                    if(!paused){pausedSinceNanos=System.nanoTime();stopAtCurrent();}paused=true;publishedUntil=index;
                    ServerExecutor.schedule(user,this::step,CrewNavigation.FRAME_MS,TimeUnit.MILLISECONDS);return;
                }
                if(paused){pausedNanos+=System.nanoTime()-pausedSinceNanos;pausedSinceNanos=0;}
                if(index==frames.size()){
                    // Allow an immediately chained route to continue without a visible stand pulse.
                    int state=user.getFoothold()>0?4:user.getMoveAction()&~1;user.setMoveAction(state|(user.isLeft()?1:0));
                    result.complete(true);
                    int endX=user.getX(),endY=user.getY();
                    ServerExecutor.schedule(user,()->{if(!active.containsKey(user.getCharacterId())&&user.getX()==endX&&user.getY()==endY)stopAtCurrent();},40,TimeUnit.MILLISECONDS);
                    return;
                }
                // Stream a short continuous native path; retain fine-grained server simulation for damage/stuns.
                if(index>=publishedUntil||paused){publishWindow();paused=false;}
                var frame=frames.get(index);boolean left=forced||frame.vx()==0?user.isLeft():frame.vx()<0;
                var elem=element(frame,left);var path=new MovePath((short)user.getX(),(short)user.getY(),(short)0,(short)0,List.of(elem));
                ServerExecutor.schedule(user,()->{
                    if(result.isDone())return;
                    if(user.getField()!=field||!user.getClient().getChannel().isActive()||user.getHp()<=0||user.getX()!=expectedX||user.getY()!=expectedY){result.complete(false);return;}
                    if(!forced&&user.getSecondaryStat().hasOption(kinoko.world.user.stat.CharacterTemporaryStat.Stun)){step();return;}
                    path.applyTo(user);expectedX=user.getX();expectedY=user.getY();index++;
                    if (!forced) CrewEncounters.checkMovingContact(user);
                    step();
                // Absolute deadlines prevent processing/queue overhead accumulating at every frame.
                },Math.max(0,startedNanos+pausedNanos+frameEnds[index]-System.nanoTime()),TimeUnit.NANOSECONDS);
            }catch(Throwable error){result.completeExceptionally(error);}
        }
    }
}
