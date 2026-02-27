package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public class HomeCommandExecutor implements CommandExecutor {
    private final SetHome plugin;
    private final HomeImporter homeImporter;
    private final Menu menu;


    public HomeCommandExecutor(SetHome plugin) {
        this.plugin = plugin;
        this.homeImporter = new HomeImporter(plugin);
        this.menu = new Menu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();
        String userCommand = config.getString("menu.open-command").replace("/", "");

        // =====================================================================
        // /sethome <name> - set home langsung via command (support spasi)
        // =====================================================================
        if (command.getName().equalsIgnoreCase("sethome")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                return true;
            }
            if (!sender.hasPermission("sethome.use")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        config.getString("messages.sethome-usage", "&eUsage: /sethome <name>")));
                return true;
            }

            // Gabung semua args jadi satu nama home (support spasi)
            String homeName = String.join(" ", args);

            String regex = config.getString("home-name-regex");

            // Jika regex tidak null dan ada, validasi — tapi kita buat regex support spasi
            // Kita skip validasi regex untuk nama dengan spasi, atau bisa custom di config
            if (regex != null && !regex.isEmpty()) {
                // Cek apakah regex yang ada support spasi, kalau tidak, skip validasi
                // agar tidak break nama dengan spasi
                if (!homeName.matches(regex)) {
                    // Coba tanpa spasi validation — cek karakter per kata
                    boolean valid = true;
                    for (String part : homeName.split(" ")) {
                        if (!part.matches(regex)) {
                            valid = false;
                            break;
                        }
                    }
                    if (!valid) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                config.getString("messages.invalid-home-name")));
                        return true;
                    }
                }
            }

            File dataFolder = new File(plugin.getDataFolder(), "data");
            if (!dataFolder.exists()) dataFolder.mkdirs();

            File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

            if (playerConfig.contains(homeName)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                return true;
            }

            List<String> homes = playerConfig.getStringList("homes");

            int maxHomes = getMaxHomesForPlayer(player);
            if (homes.size() >= maxHomes) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        config.getString("messages.home-limit-reached").replace("%limit%", String.valueOf(maxHomes))));
                return true;
            }

            Location homeLocation = player.getLocation();
            String worldName = homeLocation.getWorld().getName();

            homes.add(homeName);
            playerConfig.set("homes", homes);
            playerConfig.set(homeName + ".world", worldName);
            playerConfig.set(homeName + ".x", homeLocation.getX());
            playerConfig.set(homeName + ".y", homeLocation.getY());
            playerConfig.set(homeName + ".z", homeLocation.getZ());
            playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
            playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

            try {
                playerConfig.save(playerFile);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        config.getString("messages.home-established")).replace("%home%", homeName));
            } catch (IOException e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
            }

            return true;
        }

        // =====================================================================
        // /delhome <name> - delete home langsung via command (support spasi)
        // =====================================================================
        if (command.getName().equalsIgnoreCase("delhome")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                return true;
            }
            if (!sender.hasPermission("sethome.use")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        config.getString("messages.delhome-usage", "&eUsage: /delhome <name>")));
                return true;
            }

            // Gabung semua args jadi satu nama home (support spasi)
            String homeName = String.join(" ", args);

            File dataFolder = new File(plugin.getDataFolder(), "data");
            File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");

            if (!playerFile.exists()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                return true;
            }

            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

            if (!playerConfig.contains(homeName)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                return true;
            }

            List<String> homes = playerConfig.getStringList("homes");

            homes.remove(homeName);
            playerConfig.set("homes", homes);
            playerConfig.set(homeName, null);

            try {
                playerConfig.save(playerFile);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        config.getString("messages.home-removed")).replace("%home%", homeName));
            } catch (IOException e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
            }

            return true;
        }

        // =====================================================================
        // Command utama (/home atau yang dikonfigurasi)
        // =====================================================================
        if (command.getName().equalsIgnoreCase(userCommand)) {

            // Buka GUI jika tidak ada args
            if (args.length == 0) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("sethome.use")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions", "&cYou don't have permission to do that.")));
                        return true;
                    }
                    menu.openMainMenu(player);
                    return true;
                }
            }

            // /home admin create <player> <name> <x> <y> <z>  (nama bisa spasi, args >= 7)
            // Karena nama bisa spasi, kita handle dengan join args[3..end-3] untuk koordinat
            if (args.length >= 7 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                if (!sender.hasPermission("sethome.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
                if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Cek 3 args terakhir sebagai koordinat
                double x, y, z;
                try {
                    x = Double.parseDouble(args[args.length - 3]);
                    y = Double.parseDouble(args[args.length - 2]);
                    z = Double.parseDouble(args[args.length - 1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid-coordinates")));
                    return true;
                }

                // Nama home = args[3] sampai args[length-4]
                String[] nameArgs = new String[args.length - 6];
                System.arraycopy(args, 3, nameArgs, 0, nameArgs.length);
                String homeName = String.join(" ", nameArgs);

                String regex = config.getString("home-name-regex");
                if (regex != null && !homeName.matches(regex)) {
                    boolean valid = true;
                    for (String part : homeName.split(" ")) {
                        if (!part.matches(regex)) { valid = false; break; }
                    }
                    if (!valid) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid-home-name")));
                        return true;
                    }
                }

                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, targetPlayer.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                if (playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                    return true;
                }

                List<String> homes = playerConfig.getStringList("homes");
                String worldName = (targetPlayer.getPlayer() != null) ? targetPlayer.getPlayer().getWorld().getName() : Bukkit.getWorlds().get(0).getName();
                Location homeLocation = new Location(Bukkit.getWorld(worldName), x, y, z);

                homes.add(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName + ".world", worldName);
                playerConfig.set(homeName + ".x", x);
                playerConfig.set(homeName + ".y", y);
                playerConfig.set(homeName + ".z", z);
                playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
                playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-established-to-other")).replace("%player%", targetPlayer.getName()));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }
                return true;
            }

            // /home admin create <player> <name>  (nama bisa spasi, args >= 4)
            if (args.length >= 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                if (!sender.hasPermission("sethome.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                    return true;
                }

                Player executor = (Player) sender;

                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
                if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Nama home = join args dari index 3 ke akhir
                String[] nameArgs = new String[args.length - 3];
                System.arraycopy(args, 3, nameArgs, 0, nameArgs.length);
                String homeName = String.join(" ", nameArgs);

                String regex = config.getString("home-name-regex");
                if (regex != null && !homeName.matches(regex)) {
                    boolean valid = true;
                    for (String part : homeName.split(" ")) {
                        if (!part.matches(regex)) { valid = false; break; }
                    }
                    if (!valid) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid-home-name")));
                        return true;
                    }
                }

                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, targetPlayer.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                if (playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                    return true;
                }

                Location homeLocation = executor.getLocation();
                List<String> homes = playerConfig.getStringList("homes");

                homes.add(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName + ".world", homeLocation.getWorld().getName());
                playerConfig.set(homeName + ".x", homeLocation.getX());
                playerConfig.set(homeName + ".y", homeLocation.getY());
                playerConfig.set(homeName + ".z", homeLocation.getZ());
                playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
                playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-established-to-other")).replace("%player%", targetPlayer.getName()));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }
                return true;
            }

            // /home admin delete <player> <name>  (nama bisa spasi, args >= 4)
            if (args.length >= 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
                if (!sender.hasPermission("sethome.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
                if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Nama home = join args dari index 3 ke akhir
                String[] nameArgs = new String[args.length - 3];
                System.arraycopy(args, 3, nameArgs, 0, nameArgs.length);
                String homeName = String.join(" ", nameArgs);

                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, targetPlayer.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                if (!playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    return true;
                }

                List<String> homes = playerConfig.getStringList("homes");
                homes.remove(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName, null);

                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-removed-to-other"))
                            .replace("%player%", targetPlayer.getName())
                            .replace("%home%", homeName));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }
                return true;
            }

            // /home admin seeplayer <player>
            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("seeplayer")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                    return true;
                }

                Player admin = (Player) sender;

                if (!admin.hasPermission("sethome.admin")) {
                    admin.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (target == null || !target.hasPlayedBefore()) {
                    admin.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                menu.openPlayerHomesInventory(admin, target, 0);
                return true;
            }

            // /home create <name>  (nama bisa spasi, args >= 2)
            if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
                if (!sender.hasPermission("sethome.use")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "You must be a player to use this command.");
                    return true;
                }
                Player player = (Player) sender;

                // Gabung semua args setelah "create" jadi satu nama
                String[] nameArgs = new String[args.length - 1];
                System.arraycopy(args, 1, nameArgs, 0, nameArgs.length);
                String homeName = String.join(" ", nameArgs);

                String regex = config.getString("home-name-regex");
                if (regex != null && !homeName.matches(regex)) {
                    boolean valid = true;
                    for (String part : homeName.split(" ")) {
                        if (!part.matches(regex)) { valid = false; break; }
                    }
                    if (!valid) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid-home-name")));
                        return true;
                    }
                }

                File dataFolder = new File(plugin.getDataFolder(), "data");
                if (!dataFolder.exists()) dataFolder.mkdirs();

                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                if (playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                    return true;
                }

                List<String> homes = playerConfig.getStringList("homes");

                int maxHomes = getMaxHomesForPlayer(player);
                if (homes.size() >= maxHomes) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            config.getString("messages.home-limit-reached").replace("%limit%", String.valueOf(maxHomes))));
                    return true;
                }

                Location homeLocation = player.getLocation();
                String worldName = homeLocation.getWorld().getName();

                homes.add(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName + ".world", worldName);
                playerConfig.set(homeName + ".x", homeLocation.getX());
                playerConfig.set(homeName + ".y", homeLocation.getY());
                playerConfig.set(homeName + ".z", homeLocation.getZ());
                playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
                playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            config.getString("messages.home-established")).replace("%home%", homeName));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }
                return true;
            }

            // /home delete <name>  (nama bisa spasi, args >= 2)
            if (args.length >= 2 && args[0].equalsIgnoreCase("delete")) {
                if (!sender.hasPermission("sethome.use")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                    return true;
                }
                Player player = (Player) sender;

                // Gabung semua args setelah "delete" jadi satu nama
                String[] nameArgs = new String[args.length - 1];
                System.arraycopy(args, 1, nameArgs, 0, nameArgs.length);
                String homeName = String.join(" ", nameArgs);

                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");

                if (!playerFile.exists()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    return true;
                }

                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                if (!playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    return true;
                }

                List<String> homes = playerConfig.getStringList("homes");

                homes.remove(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName, null);

                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            config.getString("messages.home-removed")).replace("%home%", homeName));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }
                return true;
            }

            // /home reload
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("sethome.reload")) {
                    plugin.reloadConfig();
                    plugin.saveDefaultConfig();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.plugin-reloaded", "&aPlugin reloaded successfully.")));
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.no-permissions", "&cYou don't have permission to do that.")));
                }
                return true;
            }

            // /home import Essentials
            if (args.length > 1 && args[0].equalsIgnoreCase("import") && args[1].equalsIgnoreCase("Essentials")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("sethome.import.essentials")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.import-no-permission")));
                        return true;
                    }
                    homeImporter.importHomesFromEssentialsForAllPlayers(player);
                } else if (sender instanceof ConsoleCommandSender) {
                    homeImporter.importHomesFromEssentialsForAllPlayers(sender);
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.console-or-player")));
                }
                return true;
            }

            // /home import HuskHomes
            if (args.length > 1 && args[0].equalsIgnoreCase("import") && args[1].equalsIgnoreCase("HuskHomes")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("sethome.import.huskhomes")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.import-no-permission")));
                        return true;
                    }
                    homeImporter.importHomesFromHuskHomesForAllPlayers(player, "HuskHomes/HuskHomesData.db");
                } else if (sender instanceof ConsoleCommandSender) {
                    homeImporter.importHomesFromHuskHomesForAllPlayers(sender, "HuskHomes/HuskHomesData.db");
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.console-or-player")));
                }
                return true;
            }

            return false;
        }

        return false;
    }


    private int getMaxHomesForPlayer(Player player) {
        FileConfiguration config = plugin.getConfig();
        int defaultMaxHomes = config.getInt("default-maxhomes", 3);

        int maxHomes = -1;

        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String perm = permInfo.getPermission().toLowerCase();
            if (perm.startsWith("sethome.maxhomes.")) {
                try {
                    int value = Integer.parseInt(perm.replace("sethome.maxhomes.", ""));
                    if (value > maxHomes) {
                        maxHomes = value;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return maxHomes > -1 ? maxHomes : defaultMaxHomes;
    }
}