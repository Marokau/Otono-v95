package kinoko.bot;

import kinoko.provider.SkillProvider;
import kinoko.script.common.ScriptManagerImpl;
import kinoko.script.party.LudibriumPQ;
import kinoko.world.job.Job;
import kinoko.world.user.CharacterData;
import kinoko.world.user.User;

/** Modest native second-job preparation. No scrolling, HP washing or raid gear. */
public final class LpqCrewPreparation {
 public static final int LEVEL=35,ALL_CURE=2050004;
 private LpqCrewPreparation(){}
 public static Job job(KpqCrewPreparation.Role role){return switch(role){case SWORD->Job.FIGHTER;case MAGE->Job.WIZARD_IL;case ARCHER->Job.HUNTER;};}
 public static CharacterData create(int account,int id,String name,KpqCrewPreparation.Role role){return KpqCrewPreparation.create(account,id,name,role,30,LudibriumPQ.LOBBY);}
 public static void equip(User user,KpqCrewPreparation.Role role){
  if(user.getJob()==role.job.getJobId())new ScriptManagerImpl(user,user.getField(),user,"party2_enter",2040034).setJob(job(role));
  while(user.getLevel()<LEVEL){user.getCharacterStat().levelUp(user.getBasicStat().getInt());KpqCrewPreparation.allocate(user.getCharacterStat(),role);user.validateStat();}
  KpqCrewPreparation.equip(user,role,LEVEL);
  int[] secondary=switch(role){case SWORD->new int[]{1100000,1101004};case MAGE->new int[]{2201001,2200000};case ARCHER->new int[]{3100000,3101002};};
  for(int skill:secondary)if(SkillProvider.getSkillInfoById(skill).isPresent())KpqCrewPreparation.learn(user.getCharacterData(),skill,SkillProvider.getSkillInfoById(skill).orElseThrow().getMaxLevel());
  KpqCrewPreparation.supply(user,ALL_CURE,100);user.updatePassiveSkillData();user.validateStat();user.setHp(user.getMaxHp());user.setMp(user.getMaxMp());
 }
}
