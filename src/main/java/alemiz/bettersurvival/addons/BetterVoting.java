package alemiz.bettersurvival.addons;

import alemiz.bettersurvival.commands.CrateCommand;
import alemiz.bettersurvival.commands.VoteCommand;
import alemiz.bettersurvival.utils.Addon;
import alemiz.bettersurvival.utils.CustomListener;
import alemiz.bettersurvival.utils.Geometry;
import alemiz.bettersurvival.utils.fakeChest.FakeInventory;
import alemiz.bettersurvival.utils.fakeChest.FakeInventoryManager;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.particle.FloatingTextParticle;
import cn.nukkit.level.particle.HeartParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.Task;
import io.pocketvote.event.VoteDispatchEvent;
import io.pocketvote.event.VoteEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class BetterVoting extends Addon {

    public boolean enableVoteCrate = true;
    public Item voteKey = null;
    public Vector3 voteCratePos = new Vector3(0, 0, 0);
    public FloatingTextParticle voteCrateParticle = null;

    protected List<String> setters = new ArrayList<>();

    /*TODO: Implement multi-server voting system using MySql database and one server with PocketVote
    *  Must implement & create permissions system to support permission rewards - done */

    public BetterVoting(String path){
        super("bettervoting", path);

        this.enableVoteCrate = configFile.getBoolean("voteCrate");
        if (this.enableVoteCrate){
            String key = configFile.getString("voteCrateKey");
            this.voteKey = Item.fromString(key.substring(0, key.lastIndexOf(":")))
                    .setCustomName(key.substring(key.lastIndexOf(":")+1));

            this.voteCrateParticle = generateCrateParticles();
        }
    }

    @Override
    public void loadConfig() {
        if (!configFile.exists("enable")) {
            configFile.set("enable", true);

            //configFile.set("usePocketVote", true);
            configFile.set("customVoteCommand", true);
            configFile.set("customVoteCommandLink", "https://cubedmc.eu/vote");

            configFile.set("rewardPermissions", Arrays.asList("bettersurvival.feed", "bettersurvival.jump", "bettersurvival.near", "bettersurvival.vote.normal"));
            configFile.set("permissionsExpiry", 3); //days

            configFile.set("rewardItems", Arrays.asList("265:0:5", "322:0:1"));
            configFile.set("voteMessage", "§b@{player} §3has voted for this awesome server!");
            configFile.set("playerVoteMessage", "§6»@{player} thanks for voting! You received reward!");

            configFile.set("voteCrate", true);
            configFile.set("voteCratePos", "0,0,0");
            configFile.set("voteCrateKey", "388:0:§aVote key");
            configFile.set("voteCrateItems", Arrays.asList("261:0:1","354:00:1","264:0:2","368:0:1","401:0:16","373:7:1","229:9:1","397:4:1","353:0:4","438:15:1","388:0:5"));
            configFile.set("voteCrateTitle", "§6Vote §eCrate");
            configFile.set("voteCrateText", "§7Open using vote key");

            configFile.set("permission-crateCommand", "bettersurvival.cratemanage");
            configFile.set("crateKeyGiveMessageAuthor", "§6»§7Successfully sent key to §6@{target}!");
            configFile.set("crateKeyGiveMessage", "§6»§7You received crate key!");
            configFile.set("crateSetMessage", "§6»§7Please touch crate chest to get its coordinates.");
            configFile.save();
        }
    }

    @Override
    public void registerCommands() {
        registerCommand("crate", new CrateCommand("crate", this));

        if (configFile.getBoolean("customVoteCommand") &&
                plugin.getServer().getPluginManager().getPlugin("PocketVote") != null){
            registerCommand("vote", new VoteCommand("vote", this), false);
        }
    }

    @Override
    public void loadListeners() {
        if (plugin.getServer().getPluginManager().getPlugin("PocketVote") != null){
            CustomListener listener = new CustomListener(this){
                @EventHandler
                public void onVote(VoteEvent event){
                    voteReceive(event.getPlayer());

                    /* Cancel event as we handle it here*/
                    event.setCancelled();
                }

                @EventHandler
                public void onVote(VoteDispatchEvent event){
                    voteReceive(event.getPlayer());

                    /* Cancel event as we handle it here*/
                    event.setCancelled();
                }
            };
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        if (this.voteCrateParticle != null){
            plugin.getServer().getDefaultLevel().addParticle(this.voteCrateParticle, event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        Item item = event.getItem();
        Block block = event.getBlock();

        if (event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && this.setters.contains(player.getName())){
            if (block == null) return;
            this.setters.remove(player.getName());

            player.sendMessage("§6»§7You selected block at §6"+block.x +"§7, §6"+ block.y +"§7, §6"+ block.z+"§7.");
            return;
        }

        if (this.enableVoteCrate && block != null && block.getId() == Block.CHEST){
            if (!block.equals(this.voteCratePos)) return;
            event.setCancelled();

            if (item.getId() != voteKey.getId() || !item.getCustomName().equals(voteKey.getCustomName())){
                player.sendMessage("§c»§7You must have crate key. Vote to get one!");
                return;
            }
            FakeInventoryManager.createInventory(player, "Vote Chest", prepareItems());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event){
        if (this.enableVoteCrate){
            FakeInventoryManager.removeInventory(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryTranslation(InventoryTransactionEvent event){
        if (this.enableVoteCrate){
            Player player = event.getTransaction().getSource();
            if (event.getTransaction().getInventories().stream().noneMatch(inventory -> inventory instanceof FakeInventory)) return;
            event.setCancelled();

            for (InventoryAction action : event.getTransaction().getActions()){
                if (action.getSourceItem().getNamedTagEntry(FakeInventoryManager.IS_INVENTORY_ITEM) == null) continue;
                if (action.getSourceItem().getId() == Item.VINES) continue;

                Inventory inv = player.getInventory();

                for (Integer slot : inv.getContents().keySet()){
                    Item item = inv.getContents().get(slot);

                    if (!item.getCustomName().equals(this.voteKey.getCustomName()) ||
                            item.getId() != this.voteKey.getId()) continue;

                    if (item.count <= 1){
                        item = Item.get(Item.AIR, 0, 1);
                    }else {
                        item.setCount(item.count - 1);
                    }

                    inv.setItem(slot, item, true);
                    break;
                }
                player.getInventory().addItem(action.getSourceItem().clearNamedTag());
            }

            FakeInventoryManager.removeInventory(player);
        }
    }

    private Map<Integer, Item> prepareItems(){
        List<String> crateItems = new ArrayList<>(configFile.getStringList("voteCrateItems"));
        Map<Integer, Item> items = new HashMap<>();

        for (int i = 0; i < 27; i++){
            Item item = Item.get(Item.VINES);

            if (new Random().nextInt(3) == 2 && !crateItems.isEmpty()){
                String itemString = crateItems.iterator().next();
                crateItems.remove(itemString);

                item = Item.fromString(itemString);

                try {
                    item.setCount(Integer.parseInt(itemString.substring(itemString.lastIndexOf(":")+1)));
                }catch (Exception e){
                    plugin.getLogger().alert("Error while parsing Item in BetterVoting module! Date: "+itemString+" Right format: id:meta:count");
                }
            }

            items.put(i, item);
        }
        return items;
    }

    public FloatingTextParticle generateCrateParticles(){
        String[] data = configFile.getString("voteCratePos").split(",");
        if (data.length < 1) return null;
        this.voteCratePos = new Vector3(Integer.parseInt(data[0]), Integer.parseInt(data[1]), Integer.parseInt(data[2]));


        plugin.getServer().getScheduler().scheduleRepeatingTask(new Task() {
            @Override
            public void onRun(int i) {
                List<Vector3> positions = Geometry.circle(voteCratePos.add(0.5, 0.5, 0.5), 0.8, 5);
                for (Vector3 pos : positions){
                    plugin.getServer().getDefaultLevel().addParticle(new HeartParticle(pos));
                }
            }
        }, 20);

        return new FloatingTextParticle(this.voteCratePos.add(0.5, 1.7, 0.5),
               configFile.getString("voteCrateTitle"), configFile.getString("voteCrateText", (String) null));
    }

    public boolean voteReceive(String username){
        Player player = this.plugin.getServer().getPlayer(username);
        if (player == null) return false;

        try {
            List<String> rewards = this.configFile.getStringList("rewardItems");
            for (String reward : rewards){
                Item item = Item.fromString(reward.substring(0, reward.lastIndexOf(":")));
                item.setCount(Integer.parseInt(reward.substring(reward.lastIndexOf(":")+1)));

                player.getInventory().addItem(item);
            }

            if (Addon.getAddon("playerpermissions") != null && (Addon.getAddon("playerpermissions") instanceof PlayerPermissions)){
                List<String> permissions = this.configFile.getStringList("rewardPermissions");
                int expiry = this.configFile.getInt("permissionsExpiry");

                LocalDateTime date = LocalDateTime.from(new Date().toInstant().atZone(ZoneId.of("UTC"))).plusDays(expiry);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                for (String permission : permissions){
                    ((PlayerPermissions) Addon.getAddon("playerpermissions")).addPermission(player, permission, date.format(formatter));
                }
            }

            if (this.enableVoteCrate){
                Item item = this.voteKey;
                player.getInventory().addItem(item);
            }

            List<Vector3> positions = Geometry.circle(player, 1, 10);
            for (Vector3 pos : positions){
                player.getLevel().addParticle(new HeartParticle(pos));
            }

            String message = this.configFile.getString("playerVoteMessage");
            message = message.replace("{player}", player.getName());
            player.sendMessage(message);

            message = this.configFile.getString("voteMessage");
            message = message.replace("{player}", player.getName());
            plugin.getServer().broadcastMessage(message);
        }catch (Exception e){
            return false;
        }
        return true;
    }

    public void givekey(Player executor, String targetName, int count){
        if (executor == null) return;

        Item item = this.voteKey;
        item.setCount(count);

        Player target = Server.getInstance().getPlayer(targetName);
        if (target == null){
            executor.sendMessage("§6»Player §6@"+targetName+"§7 was not found!");
            return;
        }

        target.getInventory().addItem(item);

        String message = configFile.getString("crateKeyGiveMessage");
        message = message.replace("{player}", executor.getName());
        target.sendMessage(message);

        message = configFile.getString("crateKeyGiveMessageAuthor");
        message = message.replace("{player}", executor.getName());
        message = message.replace("{target}", target.getName());
        executor.sendMessage(message);
    }

    public void getCratePos(Player player){
        if (!this.setters.contains(player.getName())){
            this.setters.add(player.getName());
        }

        String message = configFile.getString("crateSetMessage");
        message = message.replace("{player}", player.getName());
        player.sendMessage(message);
    }
}
