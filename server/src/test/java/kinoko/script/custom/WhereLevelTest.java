package kinoko.script.custom;

import kinoko.bot.CrewChannel;
import kinoko.provider.MapProvider;
import kinoko.script.common.ScriptManager;
import kinoko.server.node.Client;
import kinoko.world.field.Field;
import kinoko.world.user.CharacterData;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterStat;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WhereLevelTest {
    @Test void guideTeleportsToLobbyWithoutEnteringThePartyQuest() {
        MapProvider.initialize();
        var field=new Field(null,MapProvider.getMapInfo(103000000).orElseThrow());
        var data=new CharacterData(1);var cs=new CharacterStat();cs.setHp(100);data.setCharacterStat(cs);
        var channel=new CrewChannel(null);var user=new User(new Client(null,channel),data);
        var menus=new AtomicInteger();var destinations=new ArrayList<Integer>();
        ScriptManager sm=(ScriptManager) Proxy.newProxyInstance(ScriptManager.class.getClassLoader(),new Class[]{ScriptManager.class},(proxy,method,args) -> switch(method.getName()) {
            case "getUser" -> user;
            case "getField" -> field;
            case "askMenu" -> { menus.incrementAndGet(); yield 0; }
            case "warp" -> { destinations.add((int)args[0]);yield null; }
            default -> throw new AssertionError("Unexpected guide action: "+method.getName());
        });
        try {
            WhereLevel.guide(sm);
            assertEquals(3,menus.get());assertEquals(List.of(910340700),destinations);
        } finally { field.getFieldEventFuture().cancel(true);channel.finishAndReleaseAll(); }
    }
}
