package kinoko.bot;

import kinoko.provider.ItemProvider;
import kinoko.provider.SkillProvider;
import kinoko.provider.StringProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.provider.item.ItemInfoType;
import kinoko.script.party.KerningPQ;
import kinoko.server.ServerConfig;
import kinoko.util.Util;
import kinoko.world.item.*;
import kinoko.world.job.Job;
import kinoko.world.quest.QuestManager;
import kinoko.world.skill.SkillManager;
import kinoko.world.skill.SkillRecord;
import kinoko.world.user.CharacterData;
import kinoko.world.user.User;
import kinoko.world.user.data.*;
import kinoko.world.user.stat.CharacterStat;
import kinoko.world.user.stat.ExtendSp;
import kinoko.world.user.stat.StatConstants;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Preparation for ordinary first-job KPQ characters, using Kinoko's native level-up and item data. */
public final class KpqCrewPreparation {
    public static final int LEVEL = 25;
    public static final int GINGER_ALE = 2020011;
    public static final int BOW_ARROW = 2060000;
    public enum Role {
        SWORD(Job.WARRIOR,"Sword",10,WeaponType.TH_SWORD,new int[]{1001004,1000006,1001005,1001003}),
        MAGE(Job.MAGICIAN,"Mage",8,WeaponType.STAFF,new int[]{2001005,2001002,2000006,2001003,2001004}),
        ARCHER(Job.ARCHER,"Archer",10,WeaponType.BOW,new int[]{3001005,3000001,3000002,3001003,3001004});
        public final Job job;
        public final String label;
        final int advanceLevel;
        final WeaponType weapon;
        final int[] skills;
        Role(Job job,String label,int advanceLevel,WeaponType weapon,int[] skills) {
            this.job=job;this.label=label;this.advanceLevel=advanceLevel;this.weapon=weapon;this.skills=skills;
        }
    }
    private KpqCrewPreparation() {}

    public static CharacterData create(int accountId,int characterId,String name,Role role) {
        return create(accountId,characterId,name,role,LEVEL,KerningPQ.LOBBY);
    }
    static CharacterData create(int accountId,int characterId,String name,Role role,int level,int lobby) {
        var data=new CharacterData(accountId);
        data.setItemSnCounter(new AtomicInteger(1)); data.setCreationTime(Instant.now());
        var cs=new CharacterStat(); cs.setId(characterId); cs.setName(name); cs.setGender((byte)0);
        cs.setFace(20000+role.ordinal()); cs.setHair(30000+role.ordinal()*100);
        cs.setLevel((short)1); cs.setJob((short)0); cs.setBaseStr((short)12); cs.setBaseDex((short)5);
        cs.setBaseInt((short)4); cs.setBaseLuk((short)4); cs.setSp(ExtendSp.from(new HashMap<>()));
        cs.setMaxHp(StatConstants.getMinHp(1,0)); cs.setHp(cs.getMaxHp());
        cs.setMaxMp(StatConstants.getMinMp(1,0)); cs.setMp(cs.getMaxMp());
        while(cs.getLevel()<level) {
            cs.levelUp(cs.getBaseInt());
            if(cs.getLevel()==role.advanceLevel) advance(cs,role);
            if(cs.getJob()!=0) allocate(cs,role);
        }
        cs.setAp((short)0); cs.setHp(cs.getMaxHp()); cs.setMp(cs.getMaxMp());cs.setPosMap(lobby);
        data.setCharacterStat(cs);
        var inventory=new InventoryManager(); inventory.setEquipped(new Inventory(Short.MAX_VALUE));
        inventory.setEquipInventory(new Inventory(24));inventory.setConsumeInventory(new Inventory(24));
        inventory.setInstallInventory(new Inventory(24));inventory.setEtcInventory(new Inventory(24));
        inventory.setCashInventory(new Inventory(ServerConfig.INVENTORY_CASH_SLOTS));inventory.setExtSlotExpire(Instant.now());
        inventory.setMoney(10000);data.setInventoryManager(inventory);
        data.setSkillManager(new SkillManager());
        for(int skill:role.skills) learn(data,skill,SkillProvider.getSkillInfoById(skill).orElseThrow().getMaxLevel());
        data.setQuestManager(new QuestManager());data.setConfigManager(ConfigManager.defaults());
        data.setPopularityRecord(new PopularityRecord());data.setMiniGameRecord(new MiniGameRecord());
        data.setCoupleRecord(new CoupleRecord());data.setMapTransferInfo(new MapTransferInfo());
        data.setWildHunterInfo(new WildHunterInfo());data.setFriendMax(ServerConfig.FRIEND_MAX_BASE);
        return data;
    }

    static void allocate(CharacterStat cs,Role role) {
        int available=cs.getAp();
        switch(role) {
            case SWORD -> { int dex=Math.min(Math.max(0,cs.getLevel()+5-cs.getBaseDex()),available);cs.setBaseDex((short)(cs.getBaseDex()+dex));cs.setBaseStr((short)(cs.getBaseStr()+available-dex)); }
            case MAGE -> { int luk=Math.min(Math.max(0,cs.getLevel()+3-cs.getBaseLuk()),available);cs.setBaseLuk((short)(cs.getBaseLuk()+luk));cs.setBaseInt((short)(cs.getBaseInt()+available-luk)); }
            case ARCHER -> { int str=Math.min(Math.max(0,cs.getLevel()+5-cs.getBaseStr()),available);cs.setBaseStr((short)(cs.getBaseStr()+str));cs.setBaseDex((short)(cs.getBaseDex()+available-str)); }
        }
        cs.setAp((short)0);
    }

    private static void advance(CharacterStat cs,Role role) {
        int total=cs.getBaseStr()+cs.getBaseDex()+cs.getBaseInt()+cs.getBaseLuk()+cs.getAp();
        cs.setBaseStr((short)(role==Role.SWORD?35:4));cs.setBaseDex((short)(role==Role.ARCHER?25:4));
        cs.setBaseInt((short)(role==Role.MAGE?20:4));cs.setBaseLuk((short)4);
        cs.setAp((short)(total-cs.getBaseStr()-cs.getBaseDex()-cs.getBaseInt()-cs.getBaseLuk()));
        cs.setJob(role.job.getJobId());cs.getSp().setNonExtendSp(1);
        // Same first-job bonuses as Kinoko's native job-change handler.
        if(role==Role.SWORD) cs.setMaxHp(cs.getMaxHp()+Util.getRandom(200,250));
        else if(role==Role.MAGE) cs.setMaxMp(cs.getMaxMp()+Util.getRandom(100,150));
        else { cs.setMaxHp(cs.getMaxHp()+Util.getRandom(100,150));cs.setMaxMp(cs.getMaxMp()+Util.getRandom(25,50)); }
    }

    static void learn(CharacterData data,int skillId,int requested) {
        var si=SkillProvider.getSkillInfoById(skillId).orElseThrow();
        for(var requirement:si.getReqSkills().entrySet()) {
            learn(data,requirement.getKey(),requirement.getValue());
            if(data.getSkillManager().getSkill(requirement.getKey()).map(SkillRecord::getSkillLevel).orElse(0)<requirement.getValue()) return;
        }
        SkillRecord record=data.getSkillManager().getSkill(skillId).orElseGet(() -> {
            var created=new SkillRecord(skillId);created.setMasterLevel(si.getMasterLevel());data.getSkillManager().addSkill(created);return created;
        });
        int used=Math.min(Math.max(0,Math.min(requested,si.getMaxLevel())-record.getSkillLevel()),data.getCharacterStat().getSp().getNonExtendSp());
        if(used>0) { data.getCharacterStat().getSp().removeNonExtendSp(used);record.setSkillLevel(record.getSkillLevel()+used); }
    }

    public static void equip(User user,Role role) { equip(user,role,LEVEL); }
    static void equip(User user,Role role,int levelCap) {
        allocate(user.getCharacterStat(),role);
        for(int skill:role.skills)learn(user.getCharacterData(),skill,SkillProvider.getSkillInfoById(skill).orElseThrow().getMaxLevel());
        user.updatePassiveSkillData();user.validateStat();
        for(BodyPart part:List.of(BodyPart.CAP,BodyPart.CLOTHES,BodyPart.PANTS,BodyPart.SHOES,BodyPart.GLOVES,BodyPart.WEAPON)) {
            // An overall occupies the pants position too.
            Item clothes=user.getInventoryManager().getEquipped().getItem(BodyPart.CLOTHES.getValue());
            if(part==BodyPart.PANTS && clothes!=null && clothes.getItemId()/10000==105) continue;
            ItemProvider.getItemInfos().stream().filter(ii -> suitable(user,role,part,ii,levelCap))
                    .max(Comparator.comparingInt(ItemInfo::getReqLevel)
                            .thenComparingInt(ii -> ii.getInfo(role==Role.MAGE?ItemInfoType.incMAD:ItemInfoType.incPAD))
                            .thenComparingInt(ii -> -ii.getItemId()))
                    .ifPresent(ii -> user.getInventoryManager().getEquipped().putItem(part.getValue(),ii.createItem(user.getNextItemSn())));
            user.updatePassiveSkillData();user.validateStat();
        }
        if(user.getInventoryManager().getEquipped().getItem(BodyPart.WEAPON.getValue())==null) throw new IllegalStateException("No suitable weapon for "+role);
        restock(user,role);
    }

    private static boolean suitable(User user,Role role,BodyPart part,ItemInfo ii,int levelCap) {
        if(ii.isCash() || ii.getReqLevel()>levelCap || !ItemConstants.isCorrectBodyPart(ii.getItemId(),part,user.getGender())) return false;
        if(part==BodyPart.WEAPON && WeaponType.getByItemId(ii.getItemId())!=role.weapon) return false;
        String name=StringProvider.getItemName(ii.getItemId());
        if(name==null || name.matches("(?i).*(astaroth|zakum|bandana|snowboard|valentine|christmas|timeless|reverse|maple |wolf|raven).*$")) return false;
        var stats=user.getBasicStat();
        return ii.isAbleToEquip(user.getGender(),user.getLevel(),user.getJob(),0,stats.getStr(),stats.getDex(),stats.getInt(),stats.getLuk(),0,-1,0,0);
    }

    public static void restock(User user,Role role) {
        var inventory=user.getInventoryManager();
        for(int itemId:List.of(KerningPQ.COUPON,KerningPQ.PASS,KerningPQ.PUPPET)) {
            int count=inventory.getItemCount(itemId);if(count>0) inventory.removeItem(itemId,count);
        }
        supply(user,GINGER_ALE,200);
        if(role==Role.ARCHER) supply(user,BOW_ARROW,2000);
        user.setHp(user.getMaxHp());user.setMp(user.getMaxMp());
    }

    static void supply(User user,int itemId,int desired) {
        var inventory=user.getInventoryManager();
        int count=inventory.getItemCount(itemId);
        if(count>desired) { inventory.removeItem(itemId,count-desired);return; }
        var info=ItemProvider.getItemInfo(itemId).orElseThrow();
        while(count<desired) {
            int amount=Math.min(info.getSlotMax(),desired-count);
            if(inventory.addItem(info.createItem(user.getNextItemSn(),amount)).isEmpty()) throw new IllegalStateException("Crew supply inventory is full");
            count+=amount;
        }
    }
}
