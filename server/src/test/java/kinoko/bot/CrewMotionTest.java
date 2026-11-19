package kinoko.bot;
import kinoko.provider.*;
import kinoko.script.party.KerningPQ;
import kinoko.server.node.Client;
import kinoko.world.field.Field;
import kinoko.world.user.User;
import kinoko.world.user.stat.*;
import org.junit.jupiter.api.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class CrewMotionTest {
 @BeforeAll static void providers(){MapProvider.initialize();ItemProvider.initialize();SkillProvider.initialize();StringProvider.initialize();}
 User user;Field field;CrewNavigation graph;
 @BeforeEach void fixture(){
  var data=KpqCrewPreparation.create(1,9876,"MotionTest",KpqCrewPreparation.Role.SWORD);
  var client=new Client(null,new CrewChannel(null),true);user=new User(client,data);client.setUser(user);
  var map=MapProvider.getMapInfo(KerningPQ.LOBBY).orElseThrow();field=new Field(null,map);user.setField(field);
  KpqCrewPreparation.equip(user,KpqCrewPreparation.Role.SWORD);user.setX(-174);user.setY(165);
  user.setFoothold(field.getFootholdBelow(-174,165).orElseThrow().getSn());
  graph=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);
 }
 @AfterEach void cleanup(){CrewMotion.cancel(user);field.getFieldEventFuture().cancel(true);((CrewChannel)user.getClient().getChannel()).finishAndReleaseAll();}
 @Test void stunPausesMovementThenNativeFramesReachTheLadderTop() throws Exception {
  user.setTemporaryStat(CharacterTemporaryStat.Stun,TemporaryStatOption.of(1,0,10000));
  var result=CrewMotion.moveTo(user,graph,graph.groundNode(-174,-105).orElseThrow());
  Thread.sleep(150);assertFalse(result.isDone());assertEquals(165,user.getY());
  user.resetTemporaryStat(Set.of(CharacterTemporaryStat.Stun));
  assertTrue(result.get(10,TimeUnit.SECONDS));assertEquals(-105,user.getY());
  assertEquals(4,user.getMoveAction()&~1,"Arrival must send native idle instead of leaving a walking/ladder animation running");
 }
 @Test void displacementCancelsTheOldPathInsteadOfUndoingKnockback() throws Exception {
  var result=CrewMotion.moveTo(user,graph,graph.groundNode(-174,-105).orElseThrow());
  user.setX(-160);
  assertFalse(result.get(2,TimeUnit.SECONDS));assertEquals(-160,user.getX());assertEquals(165,user.getY());
 }
 @Test void forcedKnockbackMovesEvenWhileStunnedAndDoesNotResumeTheOldRoute() throws Exception {
  user.setTemporaryStat(CharacterTemporaryStat.Stun,TemporaryStatOption.of(1,0,10000));
  var original=CrewMotion.moveTo(user,graph,graph.groundNode(-174,-105).orElseThrow());
  CrewMotion.knockback(user,1);assertFalse(original.get(1,TimeUnit.SECONDS));
  long deadline=System.currentTimeMillis()+2000;while(CrewMotion.isMoving(user)&&System.currentTimeMillis()<deadline)Thread.sleep(20);
  assertFalse(CrewMotion.isMoving(user));assertTrue(user.getX()>-174);assertEquals(165,user.getY());

 }
 @Test void idleRoutePreservesFacingInsteadOfTurningRight() throws Exception {
  user.setLeft(true);var result=CrewMotion.moveTo(user,graph,graph.groundNode(-174,165).orElseThrow());
  assertTrue(result.get(1,TimeUnit.SECONDS));assertEquals(5,user.getMoveAction());
 }
 @Test void climbingUsesNativeStateAndNegativeFootholdThenCanContinue() throws Exception {
  int middle=-1;for(int n=0;n<graph.points().size();n++){var point=graph.points().get(n);if(point.motion()==CrewNavigation.Motion.LADDER&&point.x()==-174&&point.y()>-85&&point.y()<0){middle=n;break;}}
  assertTrue(middle>=0);user.setLeft(true);assertTrue(CrewMotion.moveTo(user,graph,middle).get(10,TimeUnit.SECONDS));
  assertEquals(-1,user.getFoothold());assertEquals(15,user.getMoveAction(),"Native ladder stance must retain facing on vertical movement");
  assertTrue(CrewMotion.moveTo(user,graph,graph.groundNode(-174,-105).orElseThrow()).get(10,TimeUnit.SECONDS));assertEquals(-105,user.getY());assertEquals(5,user.getMoveAction());
 }
  @Test void nativeMovementPacketsCarryContinuousWindowsRatherThanSingleTicks() throws Exception {
  var packets=new java.util.concurrent.CopyOnWriteArrayList<kinoko.world.field.life.MovePath>();
  var observerChannel=new CrewChannel(packet->{var input=new kinoko.server.packet.NioBufferInPacket(packet.getData());if(input.getRemaining()>6&&input.decodeShort()==kinoko.server.header.OutHeader.UserMove.getValue()){input.decodeInt();packets.add(kinoko.world.field.life.MovePath.decode(input));}});
  var observerClient=new Client(null,observerChannel,true);var observer=new User(observerClient,KpqCrewPreparation.create(2,9877,"Observer",KpqCrewPreparation.Role.ARCHER));observerClient.setUser(observer);observer.setField(field);field.getUserPool().addUser(observer);
  try{
   assertTrue(CrewMotion.moveTo(user,graph,graph.groundNode(-174,-105).orElseThrow()).get(10,TimeUnit.SECONDS));
   assertTrue(packets.stream().anyMatch(path->path.getElems().size()>1&&path.getDuration()>=300),"A moving client should receive a continuous path, not a fresh correction each 50 ms");
   Thread.sleep(100);var stop=packets.getLast();assertEquals(0,stop.getDuration());assertEquals(4,stop.getElems().getLast().getMoveAction()&~1);
   assertTrue(packets.stream().filter(path->path.getDuration()>0).count()<20,"This short route must not emit one packet for every simulation tick");
  }finally{field.removeUser(observer,false);observerChannel.finishAndReleaseAll();}
 }
 @Test void midRouteStunStopsAuthoritativeMovementAndCanResume() throws Exception {
  var result=CrewMotion.moveTo(user,graph,graph.groundNode(-174,-105).orElseThrow());Thread.sleep(250);
  user.setTemporaryStat(CharacterTemporaryStat.Stun,TemporaryStatOption.of(1,0,10000));Thread.sleep(100);int y=user.getY();
  Thread.sleep(160);assertEquals(y,user.getY());assertFalse(result.isDone());
  user.resetTemporaryStat(Set.of(CharacterTemporaryStat.Stun));assertTrue(result.get(10,TimeUnit.SECONDS));assertEquals(-105,user.getY());
 }
 @Test void packetProcessingOverheadDoesNotAccumulateIntoWalkingPauses() throws Exception {
  var observerChannel=new CrewChannel(packet->{var input=new kinoko.server.packet.NioBufferInPacket(packet.getData());if(input.getRemaining()>6&&input.decodeShort()==kinoko.server.header.OutHeader.UserMove.getValue()){try{Thread.sleep(35);}catch(InterruptedException e){Thread.currentThread().interrupt();}}});
  var observerClient=new Client(null,observerChannel,true);var observer=new User(observerClient,KpqCrewPreparation.create(3,9878,"SlowObserver",KpqCrewPreparation.Role.ARCHER));observerClient.setUser(observer);observer.setField(field);field.getUserPool().addUser(observer);
  int from=graph.groundNode(-174,165).orElseThrow(),to=graph.groundNode(246,165).orElseThrow();
  long planned=graph.route(from,to).stream().flatMap(edge->edge.frames().stream()).mapToLong(CrewNavigation.Frame::milliseconds).sum();assertTrue(planned>=2500);
  try{
   long started=System.nanoTime();assertTrue(CrewMotion.moveTo(user,graph,to).get(10,TimeUnit.SECONDS));long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
   assertTrue(elapsed<planned+180,"Repeated packet-processing overhead must not extend the walk: planned="+planned+" actual="+elapsed);
  }finally{field.removeUser(observer,false);observerChannel.finishAndReleaseAll();}
 }
 @Test void consecutiveRoutesDoNotInsertAnIdlePacketAtTheirHandoff() throws Exception {
  var stops=new java.util.concurrent.atomic.AtomicInteger();
  var observerChannel=new CrewChannel(packet->{var input=new kinoko.server.packet.NioBufferInPacket(packet.getData());if(input.getRemaining()>6&&input.decodeShort()==kinoko.server.header.OutHeader.UserMove.getValue()){input.decodeInt();if(kinoko.world.field.life.MovePath.decode(input).getDuration()==0)stops.incrementAndGet();}});
  var observerClient=new Client(null,observerChannel,true);var observer=new User(observerClient,KpqCrewPreparation.create(4,9879,"HandoffObserver",KpqCrewPreparation.Role.ARCHER));observerClient.setUser(observer);observer.setField(field);field.getUserPool().addUser(observer);
  try{
   var first=CrewMotion.moveTo(user,graph,graph.groundNode(0,165).orElseThrow());
   var chain=first.thenCompose(ok->{assertTrue(ok);return CrewMotion.moveTo(user,graph,graph.groundNode(246,165).orElseThrow());});
   assertTrue(chain.get(10,TimeUnit.SECONDS));Thread.sleep(120);assertEquals(1,stops.get(),"Only the actual final destination should publish an idle stop");
  }finally{field.removeUser(observer,false);observerChannel.finishAndReleaseAll();}
 }
}
