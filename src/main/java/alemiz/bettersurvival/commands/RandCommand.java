package alemiz.bettersurvival.commands;

import alemiz.bettersurvival.addons.MoreVanilla;
import alemiz.bettersurvival.utils.Command;
import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;

public class RandCommand extends Command {

    public MoreVanilla loader;

    public RandCommand(String name, MoreVanilla loader) {
        super(name, "Teleports to random position", "", new String[]{"randtp", "randomtp"});

        this.usage = "§7/rand : Teleports to random position";
        this.setUsage(getUsageMessage());

        this.commandParameters.clear();

        this.setPermission(loader.configFile.getString("permission-randtp"));
        this.loader = loader;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (!(sender instanceof Player)){
            sender.sendMessage("§cThis command can be run only in game!");
            return true;
        }
        this.loader.randomTp((Player) sender);
        return true;
    }
}
