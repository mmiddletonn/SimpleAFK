package com.mmiddletonn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class SimpleAFK extends JavaPlugin implements Listener {

    private final HashMap<UUID, BukkitTask> playerTasks = new HashMap<>();
    private final HashMap<UUID, BukkitTask> messageTasks = new HashMap<>();
    private final HashMap<UUID, Location> originalLocations = new HashMap<>();
    private final HashSet<UUID> afkPlayers = new HashSet<>();
    private final HashSet<UUID> recentlyTeleported = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleAFK has been enabled!");
    }

    @Override
    public void onDisable() {
        playerTasks.values().forEach(BukkitTask::cancel);
        messageTasks.values().forEach(BukkitTask::cancel);
        getLogger().info("SimpleAFK has been disabled.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Ignore minor movements, like looking around
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) return;

        if (recentlyTeleported.contains(playerId)) {
            // Early return, waiting for verification of arrival
            return;
        }

        if (afkPlayers.contains(playerId)) {
            player.teleport(originalLocations.get(playerId));
            originalLocations.remove(playerId);
            afkPlayers.remove(playerId);
            getLogger().info(String.format("Returned %s to original location after detecting significant movement.", player.getName()));
            // Cancel any scheduled checks and message tasks for this player
            if (playerTasks.containsKey(playerId)) {
                playerTasks.get(playerId).cancel();
                playerTasks.remove(playerId);
            }
            if (messageTasks.containsKey(playerId)) {
                messageTasks.get(playerId).cancel();
                messageTasks.remove(playerId);
            }
            return;
        }

        // Cancel any existing task and schedule new AFK check
        playerTasks.computeIfPresent(playerId, (id, task) -> {
            task.cancel();
            return null;
        });

        originalLocations.put(playerId, player.getLocation());
        scheduleAfkCheck(player, playerId);
    }

    private void scheduleAfkCheck(Player player, UUID playerId) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            Location afkLocation = getAfkLocation();
            player.teleport(afkLocation);
            afkPlayers.add(playerId);
            recentlyTeleported.add(playerId);
            getLogger().info(String.format("Teleported %s to AFK location.", player.getName()));

            // Cancel any existing message task for this player
            if (messageTasks.containsKey(playerId)) {
                messageTasks.get(playerId).cancel();
                messageTasks.remove(playerId);
            }

            // Start a new message task that reminds the player every 5 seconds
            BukkitTask messageTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                player.sendMessage("§eYou are AFK. Move around to return to your original location.");
            }, 0L, 100L); // 0L initial delay, 100L (5 seconds) period
            
            messageTasks.put(playerId, messageTask);

            verifyArrival(player, playerId, afkLocation);
        }, getConfig().getLong("delay") * 20L); // Delay from config, in ticks

        playerTasks.put(playerId, task);
    }

    private void verifyArrival(Player player, UUID playerId, Location afkLocation) {
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (player.getLocation().getBlock().equals(afkLocation.getBlock())) {
                recentlyTeleported.remove(playerId);
                task.cancel(); // Stop this check once the player has arrived
                // Wait 1 more second before allowing movement checks again
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    // Additional logic to resume movement checks can be implemented here if needed
                }, 20L);
            }
        }, 20L, 20L); // Check every second
    }

    private Location getAfkLocation() {
        return new Location(Bukkit.getWorld(getConfig().getString("teleport.world")),
                            getConfig().getDouble("teleport.x"),
                            getConfig().getDouble("teleport.y"),
                            getConfig().getDouble("teleport.z"));
    }
}
