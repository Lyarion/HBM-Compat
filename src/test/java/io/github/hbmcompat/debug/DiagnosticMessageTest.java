package io.github.hbmcompat.debug;

import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import org.junit.Test;

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
        assertEquals(44, english.size()); // 40 reasons, chat envelope, three source labels.
        assertTrue(chinese.get("hbmcompat.debug.source.bus:export").contains("输出"));
        for (String key : english.keySet()) {
            int count = english.get(key).split("%s", -1).length - 1;
            assertEquals(key, count, chinese.get(key).split("%s", -1).length - 1);
            Object[] args = new Object[count];
            java.util.Arrays.fill(args, "value");
            assertFalse(String.format(Locale.ROOT, chinese.get(key), args).contains("%s"));
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
