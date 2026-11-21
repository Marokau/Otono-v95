package kinoko.script.party;

/** Stage dialogue and banners for the v95 Kerning and Ludibrium party quests. */
public final class PqDialogue {
    private PqDialogue() {}
    public static final String KPQ_CITY = "#e<Party Quest: First Time Together>#n\r\nHow would you like to complete a quest by working with your party members? Inside, you will find many obstacles that you will have to overcome with the help of your party members.";
    public static final String KPQ_LOBBY = "#e<Party Quest: First Time Together>#n\r\nInside, you'll find many obstacles that can only be solved by working with a party. Interested? Then have your #bParty Leader#k talk to me.";
    public static final String KPQ_FIRST = "Hello. Welcome to the first stage. Look around and you'll see Ligators wandering around. When you defeat it, it'll cough up a piece of #bcoupon#k. Every member of the party other than the leader should talk to me, get a question, and gather up the same number of #bcoupons#k as the answer to the question I'll give to them.\r\nIf you gather up the right amount of #bcoupons#k, I'll give the #bpass#k to that player. Once all the party members other than the leader gather up the #bpasses#k and give them to the leader, the leader will hand over the #bpasses#k to me, clearing the stage in the process. The faster you take care of the stages, the more stages you'll be able to challenge, so I suggest you take care of things quickly and swiftly. Well then, best of luck to you.";
    public static String kpqShortPasses(int passes) { return "I'm sorry, but you are short on the number of passes. You need to give me the right number of passes; it should be the number of members of your party minus the leader, #b" + passes + "passes#k to clear the stage. Tell your party members to solve the questions, gather up the passes, and give them to you."; }
    public static String kpqPassesClear(int passes) { return "You gathered up #b" + passes + " passes#k! Congratulation on clearing the stage! I'll make the portal that sends you to the next stage. There's a time limit on getting there, so please hurry. Best of luck to you all!"; }
    public static final String KPQ_ROPES = "Hi. Welcome to the 2nd stage. Next to me, you'll see a number of ropes. Out of these ropes, #b3 are connected to the portal that sends you to the next stage#k. All you need to do is have #b3 party members to find the answer ropes and hang on them#k.\r\nBUT, it doesn't count as an answer if you hang on the rope too low; please bring yourself up enough to be counted as a correct answer. Also, only 3 members of your party are allowed on the ropes. Once they are hanging on, the leader of the party must #bdouble-click me to check and see if the answer's correct or not#k. Now, find the right ropes to hang on!";
    public static final String KPQ_PLATFORMS = "Hello. Welcome to the 3rd stage. Next to you you'll see barrels with kittens inside on top of the platforms. Out of these platform, #b3 of them lead to the portals for the next stage. 3 of the party members need to find the correct platform to step on and clear the stage.#k\r\n#bBUT, you need to stand firm right at the center of it, not standing on the edge, in order to be counted as a correct answer, so make sure to remember that. Also, only 3 members of your party are allowed on the platforms. Once the members are on them, the leader of the party must double-click me to check and see if the answer's right or not#k. Now, find the correct platforms~!";
    public static final String KPQ_PUPPET = "If there aren't enough people to stand on the platform, purchase a #t4001454# #v4001454# from #p9020002# and place it on the correct platform.The platform will mistake #t4001454# for a character. Nifty, huh?";
    public static final String KPQ_BOSS = "Hello, welcome to the fifth and final stage. This time, you must defeat the boss, #rKing Slime#k, and collect #b1 Pass#k. Good luck!";
    public static final String KPQ_FINISHED = "Congratulations! You've cleared all the stages. If you're all done here, please talk to me, and I'll let you out.";
    public static String kpqBubble(int field) { return switch(field) {
        case KerningPQ.STAGE_1 -> "Collect the same number of Coupons as the answer to Cloto's question! You can receive the Coupons by defeating Ligators!";
        case KerningPQ.STAGE_2 -> "Find 3 ropes that can open the door to the next stage, then grab onto them!";
        case KerningPQ.STAGE_3 -> "Find the 3 Platforms that can open the door to the next stage!";
        case KerningPQ.STAGE_4 -> "Eliminate the vicious Curse Eyes!";
        case KerningPQ.STAGE_5 -> "Defeat King Slime and collect 1 Pass!";
        default -> null;
    }; }
    // Only the opening of the Red Sign sentence is visible; its short ending is reconstructed (see audit notes).
    public static final String LPQ_LOBBY = "#e<Party Quest: Dimensional Crack>#n\r\nYou can't go any higher because of the Dimensional Crack.";
    public static final String LPQ_FIRST = "Hello, there. Welcome to the 1st stage. Roam around the map and you'll find various kinds of monsters wandering all over the place. Defeat them all and collect #b25 Pass of Dimensions#k, then bring them all to me. Gather up the collected Pass of Dimensions and give them all to your party leader, who will then give them all to me. You may be familiar with those monsters, but they may be more powerful than expected, so please be careful!";
    public static final String LPQ_RATS_REMINDER = "Hello. Welcome to the first stage. There are so many Ratz from Another Dimension and Black Ratz from Another Dimension here that nibble on the Dimensional Crack. You need to defeat all those monsters before the crack gets bigger! I'll open the next stage after you eliminate all the monsters and come talk to me. Please, I'm counting on you!";
    public static final String LPQ_RATS_CLEAR = "You've eliminated all the Ratz from Another Dimension and Black Ratz from Another Dimension! Thank you! I'll open the portal that leads to the next stage. Please wait a moment.";
    public static final String LPQ_BOXES_CLEAR = "I have received all #b10 Dimensional Passes#k from you. Good work! Let me open the portal that leads to the next stage. Please wait a moment.";
    public static final String LPQ_EYES_CLEAR = "Wow, the #bShadow Eye from Another Dimension#k and the #bDark Eye from Another Dimension#k have disappeared! Thanks! I'll open the portal to the next stage now.";
    public static final String LPQ_RATS_PORTAL = "The portal to the next stage has opened.";
    public static final String LPQ_ROMBOTS_CLEAR = "Wow, not a single #bRombot from Another Dimension#k left! I'm impressed! I can open the portal to the next stage now.";
    public static final String LPQ_PORTAL_OPEN = "The portal that leads you to the next stage is now open.";
    public static final String LPQ_MISSING_MEMBERS = "I don't think every single member of your party is present. You'll need to bring each and every member from the previous stage here in order to take on this stage. Please find your missing party member(s).";
    public static final String LPQ_MATH = "Let me tell you a little bit about the sixth stage. You will find a number of platforms here, and of these platforms, #b3 of them are connected to the portal that leads to the next stage. 3 members of your party must stand on top of the correct platforms.#k\r\nEach member must stand in the center of the platform, as standing on the platform edges may not count, even if it's the correct one. Please remember that exactly 3 members must be on a platform, no more, no less. When the 3 party members are standing on a platform, the party leader must #bdouble-click on me to check whether the members have chosen the right platforms.#k Now then, here's the hint.";
    public static String lpqMathHint(String equation) { return "The #r3 numbers corresponding to the answer of my question are the key to opening the portal to the next stage.\r\n" + equation + " = ?#k\r\nPlease find the correct answer."; }
    public static final String LPQ_FINISHED = "You have defeated the monster and recovered the #bKey of Dimension#k. Thank you so much for your hard work! Thanks to you and your party, I can close the Dimensional Gate now. Let me show you the way out.";
    public static String lpqBubble(int field) { return switch(field) {
        case LudibriumPQ.STAGE_1 -> "Eliminate all the Ratz from Another Dimension and Black Ratz from Another Dimension!";
        case LudibriumPQ.BOX_STAGE -> "Break the boxes and obtain 10 Passes!";
        case LudibriumPQ.STAGE_2 -> "Eliminate Dark Eye and Shadow Eye!";
        case LudibriumPQ.STAGE_3 -> "Figure out the hidden code for the box and get to the top.";
        case LudibriumPQ.STAGE_4 -> "Eliminate all the Rombots in this map!";
        case LudibriumPQ.STAGE_5 -> "Get on the box that corresponds to the answer to the question!";
        case LudibriumPQ.BOSS -> "Defeat Alishar! Watch out for the monsters he summons.";
        default -> null;
    }; }
}
