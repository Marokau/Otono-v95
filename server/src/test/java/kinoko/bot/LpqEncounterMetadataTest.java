package kinoko.bot;
import kinoko.provider.MobProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LpqEncounterMetadataTest {
 @Test void allNativeLpqMonstersIncludingCircularRangeChronosCanBeLoaded() throws Exception {
  MobProvider.initialize();var method=CrewEncounters.class.getDeclaredMethod("metadata",int.class);method.setAccessible(true);
  for(int id:new int[]{9300005,9300006,9300008,9300014,9300010,9300012,9300015,9300016,9300017,9300018,9300019})assertDoesNotThrow(()->method.invoke(null,id),"Native monster "+id);
  var circular=method.invoke(null,9300019);var attacks=circular.getClass().getDeclaredMethod("attacks");attacks.setAccessible(true);assertTrue(((java.util.Map<?,?>)attacks.invoke(circular)).containsKey(100),"The native circular range must be preserved, not discarded");
 }
}
