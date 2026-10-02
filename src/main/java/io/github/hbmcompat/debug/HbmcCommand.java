package io.github.hbmcompat.debug;

import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

public final class HbmcCommand extends CommandBase {
    @Override public String getCommandName() { return "hbmc"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "hbmcompat.command.usage"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2 || !"debug".equals(args[0])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        DebugFilter filter = args.length == 2 ? DebugFilter.parse(args[1]) : null;
        if (args.length == 2 && filter == null && !"off".equals(args[1])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (!(sender instanceof EntityPlayerMP)) throw new CommandException("hbmcompat.command.player_only");
        java.util.UUID player = ((EntityPlayerMP) sender).getUniqueID();
        boolean enabled;
        if (args.length == 1) {
            enabled = DebugChat.SUBSCRIPTIONS.toggle(player);
            filter = DebugFilter.ALL;
        } else if (filter != null) {
            DebugChat.SUBSCRIPTIONS.enable(player, filter);
            enabled = true;
        } else {
            DebugChat.SUBSCRIPTIONS.remove(player);
            enabled = false;
        }
        ChatComponentTranslation reply = enabled
                ? new ChatComponentTranslation("hbmcompat.debug.enabled_filter",
                        new ChatComponentTranslation("hbmcompat.debug.filter." + filter.argument))
                : new ChatComponentTranslation("hbmcompat.debug.disabled");
        reply.getChatStyle().setColor(enabled ? EnumChatFormatting.GREEN : EnumChatFormatting.GOLD);
        sender.addChatMessage(reply);
    }

    @Override public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "debug");
        if (args.length == 2 && "debug".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "all", "bus", "adapter", "off");
        }
        return null;
    }
}
