package kinoko.script.custom;

import kinoko.script.common.Script;
import kinoko.script.common.ScriptHandler;
import kinoko.script.common.ScriptManager;
import kinoko.server.command.Command;
import kinoko.server.command.CommandProcessor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/** A reference menu only: choosing an entry never executes its command. */
public final class CommandGuide extends ScriptHandler {
    private static final List<String> OTONO_COMMANDS = List.of("help", "aio", "rates", "wherelevel");

    @Script("otono_commands")
    public static void commandGuide(ScriptManager sm) {
        final List<Method> commands = CommandProcessor.getCommands();
        while (true) {
            final List<Method> featured = commands.stream().filter(CommandGuide::isOtonoCommand).sorted(Comparator.comparingInt(method -> OTONO_COMMANDS.indexOf(method.getAnnotation(Command.class).value()[0]))).toList();
            final int otherCommands = featured.size();
            final Map<Integer, String> entries = new LinkedHashMap<>();
            for (int i = 0; i < featured.size(); i++) {
                entries.put(i, CommandProcessor.getHelpString(featured.get(i)).strip().split("[| ]", 2)[0]);
            }
            entries.put(otherCommands, "\r\nAll Other Commands");
            entries.put(otherCommands + 1, "\r\nClose");
            final int selected = sm.askMenu("#eOtono Commands#n\r\nChoose a command to see what it does.\r\n", entries);
            if (selected == otherCommands + 1) {
                return;
            }
            if (selected == otherCommands) {
                final List<Method> inherited = commands.stream().filter(method -> !isOtonoCommand(method)).toList();
                final Map<Integer, String> otherEntries = new LinkedHashMap<>();
                for (int i = 0; i < inherited.size(); i++) {
                    otherEntries.put(i, CommandProcessor.getHelpString(inherited.get(i)).strip().split(" ", 2)[0]);
                }
                otherEntries.put(inherited.size(), "Back");
                final int choice = sm.askMenu("#eAll Other Commands#n\r\n", otherEntries);
                if (choice < inherited.size()) {
                    showCommand(sm, inherited.get(choice));
                }
            } else {
                showCommand(sm, featured.get(selected));
            }
        }
    }

    private static boolean isOtonoCommand(Method method) {
        return OTONO_COMMANDS.contains(method.getAnnotation(Command.class).value()[0]);
    }

    private static void showCommand(ScriptManager sm, Method method) {
        sm.sayNext("#e" + CommandProcessor.getHelpString(method).strip() + "#n\r\n\r\n" +
                method.getAnnotation(Command.class).description());
    }
}
