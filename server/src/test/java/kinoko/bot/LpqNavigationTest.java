package kinoko.bot;
import kinoko.provider.MapProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LpqNavigationTest {
 @Test void crewBoxRouteUsesTheFifteenForwardPortals(){
  assertEquals("133 221 333 123 111",LpqRun.BOX_LABELS);
  MapProvider.initialize();
  var portals=MapProvider.getMapInfo(922010600).orElseThrow().getPortalInfos();
  for(int row=0;row<15;row++){
   String name=LpqRun.mazePortal(row);
   var box=portals.stream().filter(p->p.getPortalName().equals(name)).findFirst().orElseThrow();
   assertEquals(row==14?"next00":String.format("h%03d",(row+1)*3),box.getDestinationPortalName());
  }
 }
 @Test void everyPassBoxAndDarkRoomExitHasAPhysicalRoute(){
  MapProvider.initialize();
  for(int id:new int[]{922010200,922010400,922010401,922010402,922010403,922010404,922010405}){
   var map=MapProvider.getMapInfo(id).orElseThrow();var graph=new CrewNavigation(map,MapProvider.getPhysicsConstants(),100,100);var start=map.getPortalInfos().stream().filter(p->p.getPortalName().equals("st00")).findFirst().orElseThrow();int from=graph.groundNode(start.getX(),start.getY()).orElseThrow();
   for(var box:map.getReactorInfos()){int to=graph.groundNode(box.getX(),box.getY()).orElseThrow();assertTrue(from==to||!graph.route(from,to).isEmpty(),"No physical route to box in "+id);}
   for(var portal:map.getPortalInfos())if(portal.getPortalName().equals("out00")||portal.getPortalName().startsWith("in0")){
    int to=graph.groundNode(portal.getX(),portal.getY()).orElseThrow();assertTrue(from==to||!graph.route(from,to).isEmpty(),"No physical route to "+portal.getPortalName()+" in "+id);
   }
  }
 }
}
