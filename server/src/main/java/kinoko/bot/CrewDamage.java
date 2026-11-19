package kinoko.bot;
import kinoko.provider.SkillProvider;
import kinoko.provider.skill.SkillStat;
import kinoko.world.field.mob.*;
import kinoko.world.skill.*;
import kinoko.world.user.User;
import kinoko.world.user.stat.CalcDamage;

/** Client-side damage calculation for the three first-job crew roles, using Kinoko's native base range and accuracy. */
public final class CrewDamage {
 private CrewDamage(){}
 public static void calculate(User user,Mob mob,Attack attack,AttackInfo info){
  double maximum=CalcDamage.calcDamageMax(user),minimum=Math.min(maximum,CalcDamage.calcDamageMin(user));
  int percent=attack.skillId==0?100:SkillProvider.getSkillInfoById(attack.skillId).orElseThrow().getValue(SkillStat.damage,attack.slv);
  if(percent<=0)percent=100;
  int accuracy=CalcDamage.calcAccR(user,mob.getTemplate().getEva(),mob.getLevel());
  int defense=attack.isMagicAttack()?mob.getTemplate().getMdr():mob.getTemplate().getPdr();
  int rate=5+(attack.isShootAttack()?user.getSkillStatValue(3000001,SkillStat.prop):0);
  for(int hit=0;hit<attack.getDamagePerMob();hit++){
   long random=info.random[hit%7];
   if(random%100>=accuracy||mob.getMobStat().hasOption(MobTemporaryStat.Disable)){info.damage[hit]=0;continue;}
   if(mob.getMobStat().hasOption(attack.isMagicAttack()?MobTemporaryStat.MImmune:MobTemporaryStat.PImmune)){info.damage[hit]=1;continue;}
   double damage=CalcDamage.getRand(info.random[(hit+1)%7],minimum,maximum)*percent/100.0*(100-Math.clamp(defense,0,100))/100.0;
   if(info.random[(hit+2)%7]%100<rate){info.critical[hit]=1;damage*=1.35;}
   info.damage[hit]=Math.max(1,(int)damage);
  }
 }
}
