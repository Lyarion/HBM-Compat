package io.github.hbmcompat.debug;

import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

public final class HbmcCommand extends CommandBase {
    @Override public String getCommandName() { return "hbmc"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "hbmcompat.command.usage"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !"debug".equals(args[0])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (!(sender instanceof EntityPlayerMP)) throw new CommandException("hbmcompat.command.player_only");
        boolean enabled = DebugChat.SUBSCRIPTIONS.toggle(((EntityPlayerMP) sender).getUniqueID());
        sender.addChatMessage(new ChatComponentTranslation(enabled
                ? "hbmcompat.debug.enabled" : "hbmcompat.debug.disabled"));
    }

    @Override public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "debug") : null;
    }
}
