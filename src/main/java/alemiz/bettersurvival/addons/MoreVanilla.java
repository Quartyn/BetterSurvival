package alemiz.bettersurvival.addons;

import alemiz.bettersurvival.commands.*;
import alemiz.bettersurvival.utils.Addon;
import cn.nukkit.AdventureSettings;
import cn.nukkit.Server;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.player.*;
import cn.nukkit.Player;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.SpawnParticleEffectPacket;
import cn.nukkit.potion.Effect;

import java.util.*;
import java.util.stream.Collectors;

public class MoreVanilla extends Addon{

    protected Map<String, String> tpa = new HashMap<>();
    protected Map<String, Location> back = new HashMap<>();

    public MoreVanilla(String path){
        super("morevanilla", path);

        /* make sure coordinates will be shown*/
        GameRules gameRules = plugin.getServer().getDefaultLevel().getGameRules();
        gameRules.setGameRule(GameRule.SHOW_COORDINATES,
                configFile.getBoolean("showCoordinates", true));
        gameRules.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN,
                configFile.getBoolean("doImmediateRespawn", true));
    }

    @Override
    public void loadConfig() {
        if (!configFile.exists("enable")){
            configFile.set("enable", true);

            configFile.set("chatFormat", "§6{player} §7> {message}");
            configFile.set("playerNotFound", "§6»§7Player {player} was not found!");

            configFile.set("permission-fly", "bettersurvival.fly");
            configFile.set("flyMessage", "§6»§7Flying mode has been turned §6{state}§7!");

            configFile.set("permission-tpa", "bettersurvival.tpa");
            configFile.set("tpaMessage", "§6»§7Teleport request was sent to @{player}§7!");
            configFile.set("tpaRequestMessage", "§6»§7Player @{player}§7 wants teleport to you. Write §8/tpa a§7!");
            configFile.set("tpaAcceptMessage", "§6»§7Player @{player}§7 accepted your request!");
            configFile.set("tpaDennyMessage", "§6»§7Player @{player}§7 denied your request!");
            configFile.set("tpaDennyConfirmMessage", "§6»§7You denied teleport request!");
            configFile.set("tpaNoRequests", "§6»§7You dont have any requests!");

            configFile.set("permission-heal", "bettersurvival.heal");
            configFile.set("healMessage", "§6»§7You was healed!");

            configFile.set("permission-back", "bettersurvival.back");
            configFile.set("backMessage", "§6»§7You was teleported back to your death position!");
            configFile.set("backPosNotFound", "§6»§7You dont have saved any death position!");

            configFile.set("permission-feed", "bettersurvival.feed");
            configFile.set("feedMessage", "§6»§7Your feed level has been increased to {state}!");

            configFile.set("permission-near", "bettersurvival.near");
            configFile.set("nearMessage", "§6»§7Players near you: §6{players}§7!");

            configFile.set("permission-jump", "bettersurvival.jump");
            configFile.set("jumpMessage", "§6Woosh!");
            configFile.set("jumpPower", 1.5);

            configFile.set("permission-randtp", "bettersurvival.randtp");
            configFile.set("randtpMessage", "§6»§7You was teleported to random location!");

            configFile.set("showCoordinates", true);
            configFile.set("doImmediateRespawn", true);
            configFile.save();
        }
    }

    @Override
    public void registerCommands() {
        registerCommand("tpa", new TpaCommand("tpa", this));
        registerCommand("fly", new FlyCommand("fly", this));
        registerCommand("heal", new HealCommand("heal", this));
        registerCommand("feed", new FeedCommand("feed", this));
        registerCommand("back", new BackCommand("back", this));
        registerCommand("near", new NearCommand("near", this));
        registerCommand("jump", new JumpCommand("jump", this));
        registerCommand("rand", new RandCommand("rand", this));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();

        /* Set Default Permissions*/
        player.addAttachment(plugin, configFile.getString("permission-tpa"), true);
        player.addAttachment(plugin, configFile.getString("permission-back"), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();

        this.back.remove(player.getName());
    }

    @EventHandler
    public void onChat(PlayerChatEvent event){
        Player player = event.getPlayer();
        String format = configFile.getString("chatFormat");

        format = format.replace("{player}", player.getName());
        format = format.replace("{message}", event.getMessage());
        event.setFormat(format);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event){
        Player player = event.getEntity();
        this.back.put(player.getName(), player.clone());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        Item item = event.getItem();

        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;

        if (changeArmor(player, item)){
            event.setCancelled();
        }
    }

    @EventHandler
    public void onForm(PlayerFormRespondedEvent event){
        if (!(event.getWindow() instanceof FormWindowSimple) || event.getResponse() == null) return;

        FormWindowSimple form = (FormWindowSimple) event.getWindow();
        Player player = event.getPlayer();

        if (form.getTitle().equals("Player Teleport")){
            String response = form.getResponse().getClickedButton().getText();
            response = response.substring(2, response.indexOf("\n"));

            tpa(player, response);
        }
    }

    public boolean changeArmor(Player player, Item item){
        if (!item.isArmor()) return false;
        if (item.getId() == Item.SKULL) return false;

        PlayerInventory inv = player.getInventory();
        Item changed = null;

        inv.remove(item);

        if (item.isHelmet()){
            changed = inv.getHelmet();
            inv.setHelmet(item);

        }else if (item.isChestplate()){
            changed = inv.getChestplate();
            inv.setChestplate(item);

        }else if (item.isLeggings()){
            inv.setLeggings(item);

        }else if (item.isBoots()){
            changed = inv.getBoots();
            inv.setBoots(item);
        }

        if (changed != null && changed.getId() != Item.AIR){
            inv.addItem(changed);
        }
        return true;
    }

    public void tpa(Player executor, String player){
        Player pplayer = Server.getInstance().getPlayer(player);

        if (pplayer == null || !pplayer.isConnected()){
            executor.sendMessage("§6»§7Player §6@"+player+"§7 is not online!");
            return;
        }
        this.tpa.put(pplayer.getName(), executor.getName());

        String message = configFile.getString("tpaMessage");
        message = message.replace("{player}", pplayer.getName());
        executor.sendMessage(message);

        String rmessage = configFile.getString("tpaRequestMessage");
        rmessage = rmessage.replace("{player}", executor.getName());
        pplayer.sendMessage(rmessage);
    }

    public void tpaAccept(Player player){
        if (!tpa.containsKey(player.getName()) || tpa.get(player.getName()) == null){
            String message = configFile.getString("tpaNoRequests").replace("{player}", player.getName());
            player.sendMessage(message);
            return;
        }

        Player requester = Server.getInstance().getPlayer(tpa.get(player.getName()));

        if (requester == null || !requester.isConnected()){
            player.sendMessage("§cPlayer is not online!");
        }else {
            requester.teleport(player);

            String message = configFile.getString("tpaAcceptMessage").replace("{player}", player.getName());
            requester.sendMessage(message);
        }

        tpa.remove(player.getName());
    }

    public void tpaDenny(Player player){
        if (!tpa.containsKey(player.getName())){
            return;
        }

        if (tpa.get(player.getName()) != null){
            Player requester = Server.getInstance().getPlayer(tpa.get(player.getName()));
            if (requester != null && requester.isConnected()){
                String message = configFile.getString("tpaDennyMessage").replace("{player}", player.getName());
                requester.sendMessage(message);
            }
        }

        tpa.remove(player.getName());

        String message = configFile.getString("tpaDennyConfirmMessage").replace("{player}", player.getName());
        player.sendMessage(message);
    }

    public void fly(Player player, String executor){
        Player pexecutor = Server.getInstance().getPlayer(executor);
        if (!checkForPlayer(player, pexecutor)) return;

        if (executor.equals(player.getName()) && !player.hasPermission(configFile.getString("permission-fly"))){
            player.sendMessage("§cYou dont have permission to fly!");
            return;
        }

        if (!executor.equals("console") && pexecutor != null && !pexecutor.hasPermission(configFile.getString("permission-fly"))){
            pexecutor.sendMessage("§cYou dont have permission to fly!");
            return;
        }

        if (pexecutor != null && !executor.equals(player.getName())){
            pexecutor.sendMessage("§6»§7You changed flying mode of §6@"+player.getName()+"§7!");
        }

        boolean canFly = player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT);
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, !canFly);
        player.getAdventureSettings().update();

        String message = configFile.getString("flyMessage");
        message = message.replace("{player}", player.getName());
        message = message.replace("{state}", (!canFly ? "on" : "off"));
        player.sendMessage(message);
    }


    public void feed(Player player, String executor){
        Player pexecutor = Server.getInstance().getPlayer(executor);
        if (!checkForPlayer(player, pexecutor)) return;

        if (executor.equals(player.getName()) && !player.hasPermission(configFile.getString("permission-feed"))){
            player.sendMessage("§cYou dont have permission to feed!");
            return;
        }

        if (!executor.equals("console") && pexecutor != null && !pexecutor.hasPermission(configFile.getString("permission-feed"))){
            pexecutor.sendMessage("§cYou dont have permission to feed!");
            return;
        }

        if (pexecutor != null && !executor.equals(player.getName())){
            pexecutor.sendMessage("§6»§7You feeded §6@"+player.getName()+"§7!");
        }

        player.getFoodData().reset();

        String message = configFile.getString("feedMessage");
        message = message.replace("{player}", player.getName());
        message = message.replace("{state}", (String.valueOf(player.getFoodData().getLevel())));
        player.sendMessage(message);
    }

    public void heal(Player player, String executor){
        Player pexecutor = Server.getInstance().getPlayer(executor);
        if (!checkForPlayer(player, pexecutor)) return;

        if (executor.equals(player.getName()) && !player.hasPermission(configFile.getString("permission-heal"))){
            player.sendMessage("§cYou dont have permission to heal yourself!");
            return;
        }

        if (!executor.equals("console") && pexecutor != null && !pexecutor.hasPermission(configFile.getString("permission-heal"))){
            pexecutor.sendMessage("§cYou dont have permission to heal player!");
            return;
        }

        if (pexecutor != null && !executor.equals(player.getName())){
            pexecutor.sendMessage("§6»§7You healed §6@"+player.getName()+"§7!");
        }

        player.addEffect(Effect.getEffect(Effect.REGENERATION).setAmplifier(1).setDuration(5 * 20));

        String message = configFile.getString("healMessage");
        message = message.replace("{player}", player.getName());
        message = message.replace("{state}", (String.valueOf(player.getHealth())));
        player.sendMessage(message);
    }

    public void back(Player player){
        Location location = this.back.get(player.getName());
        if (location == null){
            String message = configFile.getString("backPosNotFound");
            message = message.replace("{player}", player.getName());
            player.sendMessage(message);
            return;
        }

        this.back.remove(player.getName());
        player.teleport(location);

        String message = configFile.getString("backMessage");
        message = message.replace("{player}", player.getName());
        player.sendMessage(message);
    }

    public List<Player> getNearPlayers(Location pos, int radius){
        Level level = pos.getLevel();
        List<Player> players = new ArrayList<>();

        for (int x = -radius; x < radius; x++){
            for (int z = -radius; z < radius; z++){
                players.addAll(level.getChunkPlayers(pos.getChunkX()+x, pos.getChunkZ()+z).values());
            }
        }
        return players.stream().distinct().collect(Collectors.toList());
    }

    public void jump(Player player){
        if (!player.hasPermission(configFile.getString("permission-jump"))){
            player.sendMessage("§cYou dont have permission to jump!");
            return;
        }

        int power = configFile.getInt("jumpPower");
        int x = 0;
        int z = 0;

        switch (player.getDirection().getIndex()){
            case 2:
                z = -power;
                break;
            case 3:
                z = +power;
                break;
            case 4:
                x = -power;
                break;
            case 5:
                x = +power;
                break;
        }
        Vector3 motion = new Vector3(x, power, z);


        SpawnParticleEffectPacket packet = new SpawnParticleEffectPacket();
        packet.position = player.asVector3f();
        packet.dimensionId = player.getLevel().getDimension();
        packet.identifier = "minecraft:water_evaporation_bucket_emitter";
        for (Player pplayer : player.getLevel().getPlayers().values()) pplayer.dataPacket(packet);


        player.addEffect(Effect.getEffect(Effect.DAMAGE_RESISTANCE).setAmplifier(100).setDuration(20*5).setVisible(false));
        player.setMotion(motion);

        String message = configFile.getString("jumpMessage");
        message = message.replace("{player}", player.getName());
        player.sendMessage(message);
    }

    public void randomTp(Player player){
        if (!player.hasPermission(configFile.getString("permission-randtp"))){
            player.sendMessage("§cYou dont have permission to teleport randomly!");
            return;
        }

        Random rnd = new Random();
        int x = rnd.nextInt(1500) + 50;
        int z = rnd.nextInt(1500) + 50;
        int y = 70;

        player.teleport(new Location(x, y, z, player.getLevel()));

        String message = configFile.getString("randtpMessage");
        message = message.replace("{player}", player.getName());
        player.sendMessage(message);
    }


    public boolean checkForPlayer(Player player, Player pexecutor){
        if (player == null){
            if (pexecutor != null){
                String message = configFile.getString("playerNotFound");
                message = message.replace("{player}", player.getName());
                pexecutor.sendMessage(message);
            }
            return false;
        }
        return true;
    }
}
