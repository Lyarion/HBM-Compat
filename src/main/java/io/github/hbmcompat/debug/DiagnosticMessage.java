package io.github.hbmcompat.debug;

import java.util.Locale;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

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
        ChatComponentTranslation chat = new ChatComponentTranslation(key, textArguments);
        chat.getChatStyle().setColor(color());
        return chat;
    }

    private EnumChatFormatting color() {
        String reason = key.substring("hbmcompat.debug.reason.".length());
        if ("export_success".equals(reason) || "chimney_success".equals(reason)
                || "import_attempt".equals(reason)) return EnumChatFormatting.GREEN;
        if ("no_work".equals(reason) || "bus_idle".equals(reason) || "chunk_unloaded".equals(reason)) {
            return EnumChatFormatting.GRAY;
        }
        if ("grid_error".equals(reason) || "unreadable_inputs".equals(reason)
                || "pattern_changed".equals(reason) || "recipe_unmatched".equals(reason)
                || "item_mismatch".equals(reason) || "fluid_mismatch".equals(reason)) {
            return EnumChatFormatting.RED;
        }
        return EnumChatFormatting.YELLOW;
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, english, arguments);
    }
}
