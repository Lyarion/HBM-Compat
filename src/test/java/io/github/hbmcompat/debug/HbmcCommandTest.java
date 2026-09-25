package io.github.hbmcompat.debug;

import static org.junit.Assert.*;
import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import org.junit.Test;

public class HbmcCommandTest {
    private final HbmcCommand command = new HbmcCommand();

    @Test public void commandIsPublicAndCompletesDebug() {
        assertEquals("hbmc", command.getCommandName());
        assertEquals(0, command.getRequiredPermissionLevel());
        assertTrue(command.canCommandSenderUseCommand(null));
        assertEquals("debug", command.addTabCompletionOptions(null, new String[] { "de" }).get(0));
        assertNull(command.addTabCompletionOptions(null, new String[] { "debug", "on" }));
    }

    @Test public void invalidArgumentsShowUsage() {
        for (String[] args : new String[][] { {}, { "unknown" }, { "debug", "on" } }) {
            try {
                command.processCommand(null, args);
                fail("Expected usage error");
            } catch (WrongUsageException expected) {
                assertEquals("hbmcompat.command.usage", expected.getMessage());
            }
        }
    }

    @Test public void nonPlayerCannotToggleChat() {
        try {
            command.processCommand(null, new String[] { "debug" });
            fail("Expected player-only error");
        } catch (CommandException expected) {
            assertEquals("hbmcompat.command.player_only", expected.getMessage());
        }
    }
}
