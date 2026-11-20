package kinoko.script.custom;

import kinoko.script.common.ScriptManager;
import kinoko.world.job.Job;
import kinoko.world.job.JobConstants;
import kinoko.world.job.legend.Aran;
import kinoko.world.user.stat.StatConstants;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;

final class JobAdvancement {
    private JobAdvancement() {}

    private record Choice(Job job, String name, String description, String stats, int totalStats, String gains) {}

    static void guide(ScriptManager sm) {
        while (true) {
            final int family = sm.askMenu("#eInterim Job Advancement#n\r\nChoose a class family.\r\n\r\nFYI: If a quest reward is missing, use #b@aio#k and search for the skill name. Some skills have a skill book.\r\nFor testing, use #b@levelup <x>#k to earn normal AP and SP.\r\n#b@level <x>#k skips those gains.\r\n",
                    Map.of(0, "Explorers", 1, "Resistance", 2, "Dual Blade", 3, "Cygnus Knights",
                            4, "Aran", 5, "Evan", 6, "\r\nMain Menu"));
            final String note = switch (family) {
                case 1 -> "FYI: Resistance advancement quests are scripted through fourth job for Battle Mage, Wild Hunter, and Mechanic. Full in-game audit pending.";
                case 2 -> "FYI: The Dual Blade advancement quest chain is currently incomplete. There is no complete native progression route yet.";
                case 3 -> "FYI: First-job advancement quests are implemented for all five Cygnus Knights. The second-job quest path is incomplete.";
                case 4 -> "FYI: Aran's first advancement and Combo Ability tutorial are implemented. Later advancement and skill-unlock quests are incomplete.";
                case 5 -> "FYI: Evan has native advancement handlers for all ten stages. Verify the quest chain and stage-specific SP in-game.";
                default -> null;
            };
            if (family == 0) {
                explorer(sm);
            } else if (family == 2) {
                advance(sm, DUAL_BLADE_ROUTES, note + "\r\nInterim advancement is available for Dual Blade characters.", 1);
            } else if (family == 3) {
                advance(sm, CYGNUS_ROUTES, note + "\r\nUse the native first job, then advance here at levels 30 and 70.", -1);
            } else if (family == 4) {
                advance(sm, ARAN_ROUTES, note + "\r\nComplete the native first job, then advance here at levels 30, 70, and 120.", -1);
            } else if (note != null) {
                sm.askMenu(note + "\r\n", Map.of(0, "\r\nBack"));
            } else {
                return;
            }
        }
    }

    private record Route(List<Job> jobs, List<Integer> levels) {
        static Route explorer(Job... jobs) {
            return new Route(List.of(jobs), List.of(30, 70, 120));
        }
    }

    private static final List<Route> EXPLORER_ROUTES = List.of(
            Route.explorer(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER, Job.HERO),
            Route.explorer(Job.WARRIOR, Job.PAGE, Job.WHITE_KNIGHT, Job.PALADIN),
            Route.explorer(Job.WARRIOR, Job.SPEARMAN, Job.DRAGON_KNIGHT, Job.DARK_KNIGHT),
            Route.explorer(Job.MAGICIAN, Job.WIZARD_FP, Job.MAGE_FP, Job.ARCH_MAGE_FP),
            Route.explorer(Job.MAGICIAN, Job.WIZARD_IL, Job.MAGE_IL, Job.ARCH_MAGE_IL),
            Route.explorer(Job.MAGICIAN, Job.CLERIC, Job.PRIEST, Job.BISHOP),
            Route.explorer(Job.ARCHER, Job.HUNTER, Job.RANGER, Job.BOWMASTER),
            Route.explorer(Job.ARCHER, Job.CROSSBOWMAN, Job.SNIPER, Job.MARKSMAN),
            Route.explorer(Job.ROGUE, Job.ASSASSIN, Job.HERMIT, Job.NIGHT_LORD),
            Route.explorer(Job.ROGUE, Job.BANDIT, Job.CHIEF_BANDIT, Job.SHADOWER),
            Route.explorer(Job.PIRATE, Job.BRAWLER, Job.MARAUDER, Job.BUCCANEER),
            Route.explorer(Job.PIRATE, Job.GUNSLINGER, Job.OUTLAW, Job.CORSAIR));

    private static final List<Route> CYGNUS_ROUTES = List.of(
            new Route(List.of(Job.DAWN_WARRIOR_1, Job.DAWN_WARRIOR_2, Job.DAWN_WARRIOR_3), List.of(30, 70)),
            new Route(List.of(Job.BLAZE_WIZARD_1, Job.BLAZE_WIZARD_2, Job.BLAZE_WIZARD_3), List.of(30, 70)),
            new Route(List.of(Job.WIND_ARCHER_1, Job.WIND_ARCHER_2, Job.WIND_ARCHER_3), List.of(30, 70)),
            new Route(List.of(Job.NIGHT_WALKER_1, Job.NIGHT_WALKER_2, Job.NIGHT_WALKER_3), List.of(30, 70)),
            new Route(List.of(Job.THUNDER_BREAKER_1, Job.THUNDER_BREAKER_2, Job.THUNDER_BREAKER_3), List.of(30, 70)));

    private static final List<Route> DUAL_BLADE_ROUTES = List.of(new Route(
            List.of(Job.BEGINNER, Job.ROGUE, Job.BLADE_RECRUIT, Job.BLADE_ACOLYTE, Job.BLADE_SPECIALIST,
                    Job.BLADE_LORD, Job.BLADE_MASTER), List.of(10, 20, 30, 55, 70, 120)));
    private static final List<Route> ARAN_ROUTES = List.of(new Route(
            List.of(Job.ARAN_1, Job.ARAN_2, Job.ARAN_3, Job.ARAN_4), List.of(30, 70, 120)));

    private static String name(Job job) {
        return switch (job) {
            case ROGUE -> "Thief";
            case WIZARD_FP -> "Fire/Poison Wizard";
            case WIZARD_IL -> "Ice/Lightning Wizard";
            case MAGE_FP -> "Fire/Poison Mage";
            case MAGE_IL -> "Ice/Lightning Mage";
            case ARCH_MAGE_FP -> "Fire/Poison Arch Mage";
            case ARCH_MAGE_IL -> "Ice/Lightning Arch Mage";
            case ARAN_2, ARAN_3, ARAN_4 -> "Aran, stage " + JobConstants.getJobLevel(job.getJobId());
            default -> Arrays.stream(job.name().toLowerCase(Locale.ROOT).split("_"))
                    .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));
        };
    }

    private static void advance(ScriptManager sm, List<Route> routes, String note, int requiredSubJob) {
        final int subJob = sm.getUser().getCharacterStat().getSubJob();
        final Job current = Job.getById(sm.getJob());
        final Map<Job, Integer> next = new LinkedHashMap<>();
        if (requiredSubJob < 0 || subJob == requiredSubJob) {
            for (Route route : routes) {
                final int index = route.jobs().indexOf(current);
                if (index >= 0 && index < route.levels().size()) {
                    next.put(route.jobs().get(index + 1), route.levels().get(index));
                }
            }
        }
        final var choices = List.copyOf(next.entrySet());
        final Map<Integer, String> menu = new LinkedHashMap<>();
        for (int i = 0; i < choices.size(); i++) {
            final var option = choices.get(i);
            menu.put(i, name(option.getKey()) + " (level " + option.getValue() + ")");
        }
        menu.put(choices.size(), "\r\nBack");
        while (true) {
            final int selection = sm.askMenu(note + "\r\n", menu);
            if (selection < 0 || selection >= choices.size()) return;
            final var option = choices.get(selection);
            final Job job = option.getKey();
            final int level = sm.getLevel();
            if (level < option.getValue()) {
                sm.sayOk("Reach #bLevel " + option.getValue() + "#k for this advancement.");
                continue;
            }

            final int stage = JobConstants.getJobLevel(job.getJobId());
            final int ap = job == Job.BLADE_RECRUIT || job == Job.BLADE_SPECIALIST ? 1 : stage >= 3 ? 5 : 0;
            final int sp = job == Job.BLADE_RECRUIT || job == Job.BLADE_SPECIALIST ? 0 : stage == 4 ? 3 : 1;
            final String gains = job == Job.ROGUE
                    ? "Your base stats reset to 4 STR, 25 DEX, 4 INT, and 4 LUK. You receive "
                        + ((level - 10) * 3 + 1) + " first-job SP."
                    : "Advancement grants #b" + ap + " AP and " + sp + " SP#k under the current server rules.";
            final String skillBookNote = stage == 4
                    ? "\r\nFYI: Some fourth-job skills require a skill book or quest unlock. Use #b@aio#k and search for the skill name if it has a book. Mastery books raise the cap on a skill you already know.\r\n"
                    : "";
            final int confirm = sm.askMenu("#eAdvance to " + name(job) + "#n\r\n\r\n" + gains + "\r\n" + skillBookNote,
                    Map.of(0, "Confirm", 1, "\r\nBack"));
            if (confirm == 1) continue;
            if (confirm != 0 || sm.getJob() != current.getJobId() || sm.getLevel() != level ||
                    sm.getUser().getCharacterStat().getSubJob() != subJob) return;
            sm.setJob(job);
            if (job == Job.ARAN_2) {
                sm.addSkill(Aran.POLEARM_MASTERY, 0, 0);
                sm.addSkill(Aran.FINAL_CHARGE, 0, 0);
                sm.addSkill(Aran.COMBO_SMASH, 0, 0);
                sm.addSkill(Aran.COMBO_DRAIN, 0, 0);
            } else if (job == Job.ARAN_3) {
                sm.addSkill(Aran.FULL_SWING, 0, 0);
            }
            sm.sayOk("Advancement complete. Open the skill window to review your skills.");
            return;
        }
    }

    private static void explorer(ScriptManager sm) {
        final var character = sm.getUser().getCharacterStat();
        final Job current = Job.getById(sm.getJob());
        if (character.getSubJob() == 0 && EXPLORER_ROUTES.stream().anyMatch(route -> route.jobs().contains(current))) {
            advance(sm, EXPLORER_ROUTES, "#eExplorer Advancement#n\r\nChoose the next job in your branch.", 0);
            return;
        }
        if (sm.getJob() != Job.BEGINNER.getJobId() || character.getSubJob() != 0) {
            sm.sayOk("This advancement is for standard Explorer Beginners.\r\n\r\nFor testing, use #b@job 0#k to switch your job ID back to Beginner. This does not reset stats or skills.");
            return;
        }

        while (true) {
            final int selection = sm.askMenu("#eInterim Job Advancement#n\r\nChoose your first Explorer job.\r\n",
                    Map.of(0, "Warrior", 1, "Archer", 2, "Thief", 3, "Magician", 4, "Pirate", 5, "\r\nBack"));
            final Choice choice = switch (selection) {
                case 0 -> new Choice(Job.WARRIOR, "Warrior", "Warriors fight up close with melee weapons.",
                        "35 STR, 4 DEX, 4 INT, and 4 LUK", 47, "Max HP increases by 200 to 250.");
                case 1 -> new Choice(Job.ARCHER, "Archer", "Archers attack from a distance with bows or crossbows.",
                        "4 STR, 25 DEX, 4 INT, and 4 LUK", 37, "Max HP increases by 100 to 150 and max MP by 25 to 50.");
                case 2 -> new Choice(Job.ROGUE, "Thief", "Thieves fight with daggers or throwing stars.",
                        "4 STR, 25 DEX, 4 INT, and 4 LUK", 37, "Max HP increases by 100 to 150 and max MP by 25 to 50.");
                case 3 -> new Choice(Job.MAGICIAN, "Magician", "Magicians cast spells with wands or staves.",
                        "4 STR, 4 DEX, 20 INT, and 4 LUK", 32, "Max MP increases by 100 to 150.");
                case 4 -> new Choice(Job.PIRATE, "Pirate", "Pirates fight with knuckles or guns.",
                        "4 STR, 20 DEX, 4 INT, and 4 LUK", 32, "Max HP increases by 100 to 150 and max MP by 25 to 50.");
                default -> null;
            };
            if (choice == null) return;

            final int level = sm.getLevel();
            final int required = JobConstants.getJobChangeLevel(choice.job().getJobId(), 0, 1);
            if (level < required) {
                sm.sayOk("Reach #bLevel " + required + "#k to advance to " + choice.name() + ".");
                return;
            }

            final int availableAp = StatConstants.getSumAp(level, choice.job().getJobId(), 0) - choice.totalStats();
            final int skillPoints = (level - required) * 3 + 1;
            final int confirmation = sm.askMenu("#eAdvance to " + choice.name() + "#n\r\n\r\n" +
                    choice.description() + "\r\n\r\n" +
                    (choice.job() == Job.MAGICIAN ? "FYI: The native first-job Magician quest is implemented.\r\n\r\n" : "") +
                    "Your base stats will be reset to #b" + choice.stats() + "#k.\r\n" +
                    "You will have #b" + availableAp + " unspent AP and " + skillPoints + " first-job SP#k.\r\n" +
                    choice.gains() + "\r\n\r\n" +
                    "Choose Confirm to advance.\r\n", Map.of(0, "Confirm", 1, "\r\nBack"));
            if (confirmation == 1) continue;
            if (confirmation != 0) return;

            if (sm.getJob() != Job.BEGINNER.getJobId() || character.getSubJob() != 0 || sm.getLevel() != level) {
                sm.sayOk("Your character changed. Open the guide again to review your advancement.");
                return;
            }
            sm.setJob(choice.job());
            sm.sayOk("You are now a " + choice.name() + ". Open your skill window to spend your SP.");
            return;
        }
    }
}
