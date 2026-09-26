package io.github.hbmcompat.debug;

import java.util.Locale;
import net.minecraft.util.ChatComponentTranslation;

/** Client-translated chat with a stable English representation for logs and deduplication. */
public final class DiagnosticMessage {
    private final String key;
    private final String english;
    private final Object[] arguments;

    private DiagnosticMessage(String key, String english, Object[] arguments) {
        this.key = "hbmcompat.debug.reason." + key;
        this.english = english;
        this.arguments = arguments.clone();
    }

    public static DiagnosticMessage of(String key, String english, Object... arguments) {
        return new DiagnosticMessage(key, english, arguments);
    }

    public ChatComponentTranslation toChat() {
        Object[] textArguments = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) textArguments[i] = String.valueOf(arguments[i]);
        return new ChatComponentTranslation(key, textArguments);
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, english, arguments);
    }
}
