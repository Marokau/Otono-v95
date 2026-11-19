package kinoko.bot;
import kinoko.provider.MapProvider;
import kinoko.script.party.KerningPQ;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CrewNavigationTest {
 @BeforeAll static void load(){MapProvider.initialize();}
 @Test void stageOneSpawnCanReachItsExitAndReturnWithoutWarps(){
  var map=MapProvider.getMapInfo(KerningPQ.STAGE_1).orElseThrow();
  var nav=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);
  int start=nav.groundNode(-1075,106).orElseThrow(),exit=nav.groundNode(716,106).orElseThrow();
  assertFalse(nav.route(start,exit).isEmpty());assertFalse(nav.route(exit,start).isEmpty());
  for(var point:nav.points())if(point.foothold()!=0){
   var fh=map.getFootholds().stream().filter(f->f.getSn()==point.foothold()).findFirst().orElseThrow();
   assertTrue(point.x()>=fh.getX1()&&point.x()<=fh.getX2());assertEquals(fh.getYFromX(point.x()),point.y());
  }
 }
 @Test void lobbyLadderRouteHasClimbFramesAndStaysOnRealLadder(){
  var map=MapProvider.getMapInfo(KerningPQ.LOBBY).orElseThrow();
  var nav=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);
  int bottom=nav.groundNode(-174,165).orElseThrow(),top=nav.groundNode(-174,-105).orElseThrow();
  var route=nav.route(bottom,top);assertFalse(route.isEmpty());
  assertTrue(route.stream().flatMap(e->e.frames().stream()).anyMatch(f->f.point().motion()==CrewNavigation.Motion.LADDER));
  for(var frame:route.stream().flatMap(e->e.frames().stream()).toList()) {
   if(frame.point().motion()==CrewNavigation.Motion.LADDER){assertEquals(-174,frame.point().x());assertTrue(frame.point().y()>=-105&&frame.point().y()<=165);}
   assertTrue(frame.milliseconds()>0);
  }
 }
 @Test void stageOneMonsterPlatformsHaveAReachableApproach(){
  var map=MapProvider.getMapInfo(KerningPQ.STAGE_1).orElseThrow();
  var nav=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);
  int start=nav.groundNode(-1075,106).orElseThrow();
  var monsters=map.getLifeInfos().stream().filter(life->life.getLifeType()==kinoko.provider.map.LifeType.MOB).toList();
  assertFalse(monsters.isEmpty(),"Must inspect real stage-one monster spawns");
  for(var life:monsters){
   int target=nav.groundNode(life.getX(),life.getCy()-1).orElseThrow();
   assertTrue(start==target||!nav.route(start,target).isEmpty(),"Unreachable native Ligator spawn: "+life);
   assertTrue(start==target||!nav.route(target,start).isEmpty(),"No return from native Ligator spawn: "+life);
  }
 }
 @Test void everyDownJumpHonorsNativeSurfaceRestrictions(){
  var map=MapProvider.getMapInfo(KerningPQ.STAGE_1).orElseThrow();
  var nav=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);int drops=0;
  for(int i=0;i<nav.points().size();i++)for(var edge:nav.edgesFrom(i)) {
   if(edge.frames().stream().anyMatch(f->f.point().motion()==CrewNavigation.Motion.DROP)) {
    drops++;int id=nav.points().get(i).foothold();
    var fh=map.getFootholds().stream().filter(f->f.getSn()==id).findFirst().orElseThrow();
    assertEquals(0,fh.getForbidFallDown());assertEquals(0,fh.getCantThrough());
   }
  }
  assertTrue(drops>0,"Test must examine actual down-jump routes");
 } @Test void everyPuzzleAreaHasALegalRouteAndAnExitRoute(){
  for(int id:new int[]{KerningPQ.STAGE_2,KerningPQ.STAGE_3}){
   var map=MapProvider.getMapInfo(id).orElseThrow();var nav=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);
   var entry=map.getPortalInfos().stream().filter(portal->portal.getPortalName().equals("st00")).findFirst().orElseThrow();
   var exit=map.getPortalInfos().stream().filter(portal->portal.getPortalName().equals("next000")).findFirst().orElseThrow();
   int start=nav.groundNode(entry.getX(),entry.getY()).orElseThrow(),end=nav.groundNode(exit.getX(),exit.getY()).orElseThrow();
   var goals=new java.util.ArrayList<Integer>();
   for(var area:map.getAreas()){
    int selected=-1;double score=Double.MAX_VALUE;
    for(int i=0;i<nav.points().size();i++){
     var point=nav.points().get(i);
     if(!area.isInsideRect(point.x(),point.y())||(id==KerningPQ.STAGE_2?point.motion()!=CrewNavigation.Motion.ROPE:point.motion()!=CrewNavigation.Motion.WALK))continue;
     double distance=Math.abs(point.x()-area.getCenterX())+Math.abs(point.y()-area.getCenterY())*1.4;
     if(distance<score){score=distance;selected=i;}
    }
    assertTrue(selected>=0,"No physical point in puzzle area "+id+" "+area);
    assertFalse(nav.route(start,selected).isEmpty(),"No route into puzzle area "+id+" "+area);
    assertFalse(nav.route(selected,end).isEmpty(),"No route from puzzle area to exit "+id+" "+area);goals.add(selected);
   }
   for(int from:goals)for(int to:goals)if(from!=to)assertFalse(nav.route(from,to).isEmpty(),"Cannot change puzzle arrangement in "+id);
  }
 }
 @Test void hangingRopesCannotGenerateVerticalJumpShortcuts(){
  var nav=new CrewNavigation(MapProvider.getMapInfo(KerningPQ.STAGE_1).orElseThrow(),MapProvider.getPhysicsConstants(),100,100);int climbs=0;
  for(int n=0;n<nav.points().size();n++){
   var origin=nav.points().get(n);if(origin.motion()!=CrewNavigation.Motion.ROPE&&origin.motion()!=CrewNavigation.Motion.LADDER)continue;
   for(var edge:nav.edgesFrom(n)){
    boolean airborne=edge.frames().stream().anyMatch(f->f.point().motion()==CrewNavigation.Motion.AIR);
    if(!airborne){climbs++;continue;}
    assertTrue(edge.frames().stream().anyMatch(f->Math.abs(f.point().x()-origin.x())>5),"A rope dismount must actually leave the rope horizontally");
   }
  }assertTrue(climbs>0);
 }
}
