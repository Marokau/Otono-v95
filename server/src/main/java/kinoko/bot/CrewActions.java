package kinoko.bot;

import kinoko.handler.field.FieldHandler;
import kinoko.handler.user.*;
import kinoko.handler.user.item.ItemHandler;
import kinoko.provider.SkillProvider;
import kinoko.script.common.ScriptMessageType;
import kinoko.server.header.OutHeader;
import kinoko.server.node.ServerExecutor;
import kinoko.server.packet.*;
import kinoko.world.field.drop.Drop;
import kinoko.world.field.mob.Mob;
import kinoko.world.item.*;
import kinoko.world.skill.*;
import kinoko.world.user.User;
import kinoko.world.user.stat.CharacterTemporaryStat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Small client-equivalent requests. No direct stage flags, inventory grants or monster HP changes. */
public final class CrewActions {
    private CrewActions() {}
    private static InPacket input(OutPacket packet){return new NioBufferInPacket(packet.getData());}
    public static int position(User user,int itemId){
        var inventory=user.getInventoryManager().getInventoryByType(InventoryType.getByItemId(itemId));
        return inventory.getItems().entrySet().stream().filter(e->e.getValue().getItemId()==itemId).mapToInt(e->e.getKey()).findFirst().orElse(0);
    }
    public static boolean potion(User user){
        int position=position(user,KpqCrewPreparation.GINGER_ALE);if(position==0||user.getHp()<=0)return false;
        var p=OutPacket.of();p.encodeInt(0);p.encodeShort(position);p.encodeInt(KpqCrewPreparation.GINGER_ALE);
        ItemHandler.handleUserStatChangeItemUseRequest(user,input(p));return true;
    }
    public static boolean hitBox(User user,kinoko.world.field.reactor.Reactor box){
        if(user.hasDialog()||user.getHp()<=0||user.getSecondaryStat().hasOption(CharacterTemporaryStat.Stun)||box.isLastState()||Math.abs(user.getX()-box.getX())>75||Math.abs(user.getY()-box.getY())>65)return false;
        var attack=new Attack(OutHeader.UserMeleeAttack);attack.attackSpeed=6;attack.actionAndDir=(short)(ActionType.SWINGT1.getValue()|(box.getX()<user.getX()?0x8000:0));attack.userX=(short)user.getX();attack.userY=(short)user.getY();
        user.getField().broadcastPacket(kinoko.packet.user.UserRemote.attack(user,attack));
        var p=OutPacket.of();p.encodeInt(box.getId());p.encodeInt(0);p.encodeInt(0);p.encodeShort(0);p.encodeInt(0);FieldHandler.handleReactorHit(user,input(p));return true;
    }
    public static boolean pickUp(User user,Drop drop){
        if(Math.abs(user.getX()-drop.getX())>30||Math.abs(user.getY()-drop.getY())>35||!drop.canPickUp(user))return false;
        var p=OutPacket.of();p.encodeByte(user.getFieldKey());p.encodeInt(0);p.encodeShort(user.getX());p.encodeShort(user.getY());p.encodeInt(drop.getId());p.encodeInt(0);
        FieldHandler.handleDropPickUpRequest(user,input(p));return user.getField().getDropPool().getById(drop.getId()).isEmpty();
    }
    public static boolean drop(User user,int itemId,int count){
        int position=position(user,itemId);if(position==0||count<=0||user.getInventoryManager().getItemCount(itemId)<count)return false;
        var p=OutPacket.of();p.encodeInt(0);p.encodeByte(InventoryType.getByItemId(itemId).getValue());p.encodeShort(position);p.encodeShort(0);p.encodeShort(count);
        UserHandler.handleUserChangeSlotPositionRequest(user,input(p));return true;
    }
    public static boolean talk(User user,int template){
        if(user.hasDialog())return false;
        var npc=user.getField().getNpcPool().getByTemplateId(template).orElse(null);
        if(npc==null||Math.abs(user.getX()-npc.getX())>400||Math.abs(user.getY()-npc.getY())>300)return false;
        var p=OutPacket.of();p.encodeInt(npc.getId());p.encodeShort(user.getX());p.encodeShort(user.getY());UserHandler.handleUserSelectNpc(user,input(p));return true;
    }
    public static boolean portal(User user,String name){
        var portal=user.getField().getPortalByName(name).orElse(null);
        if(portal==null||Math.abs(user.getX()-portal.getX())>35||Math.abs(user.getY()-portal.getY())>65)return false;
        var p=OutPacket.of();p.encodeByte(user.getFieldKey());p.encodeString(name);p.encodeShort(user.getX());p.encodeShort(user.getY());UserHandler.handleUserPortalScriptRequest(user,input(p));return true;
    }
    public static void answer(User user,OutPacket outbound,int menu){
        var p=new NioBufferInPacket(outbound.getData());if(p.getRemaining()<8||p.decodeShort()!=OutHeader.ScriptMessage.getValue())return;
        p.decodeByte();p.decodeInt();byte type=p.decodeByte();var dialog=user.getDialog();
        ServerExecutor.schedule(user,()->{
            if(user.getClient().getUser()!=user||!user.hasDialog()||user.getDialog()!=dialog)return;
            var response=OutPacket.of();response.encodeByte(type);response.encodeByte(1);
            if(type==ScriptMessageType.ASKMENU.getValue())response.encodeInt(menu);
            UserHandler.handleUserScriptMessageAnswer(user,input(response));
        },150,TimeUnit.MILLISECONDS);
    }
    public static boolean buff(User user,int skillId){
        int level=user.getSkillLevel(skillId);if(level==0)return false;
        var p=OutPacket.of();p.encodeInt(0);p.encodeInt(skillId);p.encodeByte(level);p.encodeShort(0);
        SkillHandler.handleUserSkillUseRequest(user,input(p));return true;
    }
    public static boolean attack(User user,Mob target,KpqCrewPreparation.Role role){
        if(!user.isAutomated()||user.hasDialog()||user.getHp()<=0||user.getSecondaryStat().hasOption(CharacterTemporaryStat.Stun))return false;
        int skill=switch(role){case SWORD->1001004;case MAGE->2001005;case ARCHER->3001005;};
        if(user.getSecondaryStat().hasOption(CharacterTemporaryStat.Seal))skill=0;
        int horizontal=role==KpqCrewPreparation.Role.SWORD?90:230;
        if (skill != 2001005 && (Math.abs(user.getX()-target.getX())>horizontal||Math.abs(user.getY()-target.getY())>65)) return false;
        if (skill == 2001005 && !canCastMagicClaw(user, target, user.getX(), user.getY())) return false;
        boolean left=target.getX()<user.getX();
        var header=switch(role){case SWORD->OutHeader.UserMeleeAttack;case MAGE->skill==0?OutHeader.UserMeleeAttack:OutHeader.UserMagicAttack;case ARCHER->OutHeader.UserShootAttack;};
        var attack=new Attack(header);attack.skillId=skill;attack.slv=user.getSkillLevel(skill);attack.attackSpeed=6;
        int hits=skill==2001005||skill==3001005?2:1;attack.mask=(byte)(0x10|hits);
        int action=switch(role){case SWORD->ActionType.SWINGT1.getValue();case MAGE->skill==0?ActionType.SWINGT1.getValue():ActionType.MAGICATTACK2.getValue();case ARCHER->ActionType.SHOOT1.getValue();};
        attack.actionAndDir=(short)(action|(left?0x8000:0));attack.userX=(short)user.getX();attack.userY=(short)user.getY();
        attack.ballStartX=(short)user.getX();attack.ballStartY=(short)(user.getY()-20);
        if(skill!=0)attack.crc=SkillProvider.getSkillInfoById(skill).orElseThrow().getLevelDataCrc(attack.slv);
        if(role==KpqCrewPreparation.Role.ARCHER){attack.bulletPosition=(short)position(user,KpqCrewPreparation.BOW_ARROW);if(attack.bulletPosition==0)return false;}
        addAttackTarget(attack, target);
        if (skill == 2001005) {
            var second = new ArrayList<Mob>();
            user.getField().getMobPool().forEach(mob -> {
                if (mob != target && mob.getHp() > 0 && (mob.getX() < user.getX()) == left &&
                        canCastMagicClaw(user, mob, user.getX(), user.getY())) second.add(mob);
            });
            second.stream().min(Comparator.comparingDouble(mob ->
                    Math.hypot(mob.getX() - user.getX(), mob.getY() - user.getY())))
                    .ifPresent(mob -> addAttackTarget(attack, mob));
        }
        attack.mask = (byte)((attack.getAttackInfo().size() << 4) | hits);
        AttackHandler.handleAutomatedAttack(user, attack);
        return true;
    }

    private static void addAttackTarget(Attack attack, Mob target) {
        var info = new AttackInfo();
        info.mobId = target.getId();
        info.mob = target;
        info.hitX = (short)target.getX();
        info.hitY = (short)target.getY();
        info.delay = 100;
        if (attack.skillId == 2001005) {
            int targetAction = (target.getMoveAction() >>> 1) & 0x7f;
            // The native cast trace reports walk or stand for ground targets.
            // Avoid replaying a transient airborne pose for a grounded target.
            if (target.getFoothold() > 0 && targetAction == kinoko.world.field.mob.MobActionType.JUMP.getValue()) {
                targetAction = kinoko.world.field.mob.MobActionType.STAND.getValue();
            }
            info.actionAndDir = (byte)(targetAction | (target.isLeft() ? 0x80 : 0));
            info.delay = 450 + 50 * attack.getAttackInfo().size();
        }
        attack.getAttackInfo().add(info);
    }
    static boolean canCastMagicClaw(User user, Mob target, int x, int y) {
        int level = user.getSkillLevel(2001005);
        var range = SkillProvider.getSkillInfoById(2001005).map(skill -> skill.getRect(level)).orElse(null);
        if (range == null) {
            return Math.abs(x - target.getX()) <= 230 && Math.abs(y - target.getY()) <= 35;
        }
        var facingRange = target.getX() < x ? range : range.flipX();
        return facingRange.translate(x, y).isInsideRect(target.getX(), target.getY());
    }
}
