package kinoko.world.user.stat;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class BeginnerAbilityTest {
    @Test void automaticBeginnerDistributionSpendsExactlyTheEarnedBudget() {
        var cs=new CharacterStat();cs.setLevel((short)1);cs.setJob((short)0);
        cs.setBaseStr((short)12);cs.setBaseDex((short)5);cs.setBaseInt((short)4);cs.setBaseLuk((short)4);
        cs.setMaxHp(50);cs.setMaxMp(50);cs.setSp(ExtendSp.from(new HashMap<>()));
        for(int level=2;level<=12;level++) {
            int before=cs.getBaseStr()+cs.getBaseDex()+cs.getBaseInt()+cs.getBaseLuk()+cs.getAp();
            cs.levelUp(cs.getBaseInt());
            assertEquals(StatConstants.getIncAp(level,0),cs.getBaseStr()+cs.getBaseDex()+cs.getBaseInt()+cs.getBaseLuk()+cs.getAp()-before);
        }
    }
}
