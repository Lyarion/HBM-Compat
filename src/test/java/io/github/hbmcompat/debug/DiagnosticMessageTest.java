package io.github.hbmcompat.debug;

import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import org.junit.Test;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.EnumChatFormatting;

public class DiagnosticMessageTest {
    @Test public void chatRetainsTranslationKeyAndArgumentsWhileLogsStayEnglish() {
        DiagnosticMessage message = DiagnosticMessage.of("tank_full", "input tank for %s is full", "water");
        assertEquals("input tank for water is full", message.toString());
        assertEquals("hbmcompat.debug.reason.tank_full", message.toChat().getKey());
        assertArrayEquals(new Object[] { "water" }, message.toChat().getFormatArgs());
    }

    @Test public void bothLanguagesCoverEveryDiagnosticAndAcceptTheSameArguments() throws Exception {
        Map<String, String> english = translations("en_US");
        Map<String, String> chinese = translations("zh_CN");
        assertEquals(english.keySet(), chinese.keySet());
        assertEquals(47, english.size()); // 43 reasons, chat envelope, three source labels.
        assertTrue(chinese.get("hbmcompat.debug.source.bus:export").contains("输出"));
        for (String key : english.keySet()) {
            int count = english.get(key).split("%s", -1).length - 1;
            assertEquals(key, count, chinese.get(key).split("%s", -1).length - 1);
            Object[] args = new Object[count];
            java.util.Arrays.fill(args, "value");
            assertFalse(String.format(Locale.ROOT, chinese.get(key), args).contains("%s"));
        }
    }

    @Test public void chatColorsSeparateDeviceLocationAndOutcome() {
        net.minecraft.util.ChatComponentTranslation line = DebugChat.format("push", 0, "1,2,3",
                DiagnosticMessage.of("item_mismatch", "Mismatch: %s", "iron"));
        assertEquals(EnumChatFormatting.GRAY, line.getChatStyle().getColor());
        Object[] parts = line.getFormatArgs();
        assertEquals(EnumChatFormatting.GOLD, ((IChatComponent) parts[0]).getChatStyle().getColor());
        assertTrue(((IChatComponent) parts[0]).getChatStyle().getBold());
        assertEquals(EnumChatFormatting.AQUA, ((IChatComponent) parts[2]).getChatStyle().getColor());
        assertEquals(EnumChatFormatting.RED, ((IChatComponent) parts[3]).getChatStyle().getColor());
        assertEquals(EnumChatFormatting.GREEN,
                DiagnosticMessage.of("export_success", "Success").toChat().getChatStyle().getColor());
        assertEquals(EnumChatFormatting.YELLOW,
                DiagnosticMessage.of("tank_full", "Full").toChat().getChatStyle().getColor());
        assertEquals(EnumChatFormatting.GRAY,
                DiagnosticMessage.of("no_work", "Idle").toChat().getChatStyle().getColor());
        for (String label : new String[] { "bus:fluid-import", "bus:export" }) {
            Object device = DebugChat.format(label, 0, "1,2,3",
                    DiagnosticMessage.of("no_work", "Idle")).getFormatArgs()[0];
            assertEquals("bus:fluid-import".equals(label) ? EnumChatFormatting.BLUE
                    : EnumChatFormatting.LIGHT_PURPLE, ((IChatComponent) device).getChatStyle().getColor());
        }
    }

    private Map<String, String> translations(String language) throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                "/assets/hbmcompat/lang/" + language + ".lang"), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("hbmcompat.debug.reason.") && !line.startsWith("hbmcompat.debug.source.")
                        && !line.startsWith("hbmcompat.debug.line=")) continue;
                int separator = line.indexOf('=');
                assertNull("Duplicate key: " + line, result.put(line.substring(0, separator), line.substring(separator + 1)));
            }
        }
        return result;
    }
}
