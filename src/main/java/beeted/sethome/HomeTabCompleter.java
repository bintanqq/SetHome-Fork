package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HomeTabCompleter implements TabCompleter {

    private final SetHome plugin;

    public HomeTabCompleter(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> suggestions = new ArrayList<>();
        FileConfiguration config = plugin.getConfig();
        String configuredCommand = config.getString("menu.open-command", "/home").replace("/", "");

        if (!(sender instanceof Player)) return suggestions;
        Player player = (Player) sender;

        // =====================================================================
        // /sethome <n>  — suggest "<home_name>" as hint
        // =====================================================================
        if (command.getName().equalsIgnoreCase("sethome")) {
            if (args.length >= 1) {
                suggestions.add("<home_name>");
            }
            return suggestions;
        }

        // =====================================================================
        // /delhome <n>  — suggest player's existing homes (support spasi)
        // =====================================================================
        if (command.getName().equalsIgnoreCase("delhome")) {
            if (args.length >= 1) {
                // Rekonstruksi input sejauh ini untuk matching prefix
                String currentInput = String.join(" ", args).toLowerCase();

                List<String> homes = plugin.getHomesFor(player);
                for (String home : homes) {
                    if (home.toLowerCase().startsWith(currentInput)) {
                        // Suggest sisa nama dari kata yang sedang diketik
                        suggestions.add(home);
                    }
                }

                if (suggestions.isEmpty()) {
                    suggestions.add("<home_name>");
                }
            }
            return suggestions;
        }

        // =====================================================================
        // Command utama (/home atau yang dikonfigurasi)
        // =====================================================================
        if (command.getName().equalsIgnoreCase(configuredCommand) || alias.equalsIgnoreCase(configuredCommand)) {

            // /home <...>  — arg pertama
            if (args.length == 1) {
                if (player.hasPermission("sethome.reload")) suggestions.add("reload");
                if (player.hasPermission("sethome.import.essentials") || player.hasPermission("sethome.import.huskhomes"))
                    suggestions.add("import");
                if (player.hasPermission("sethome.admin")) suggestions.add("admin");
                suggestions.add("create");
                suggestions.add("delete");
            }

            // /home import <Essentials/HuskHomes>
            if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
                if (player.hasPermission("sethome.import.essentials")) suggestions.add("Essentials");
                if (player.hasPermission("sethome.import.huskhomes")) suggestions.add("HuskHomes");
            }

            // /home create <n>  — suggest hint atau homes yang belum ada
            if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
                suggestions.add("<home_name>");
            }

            // /home delete <n>  — suggest existing homes (support spasi partial match)
            if (args.length >= 2 && args[0].equalsIgnoreCase("delete")) {
                String currentInput = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
                List<String> homes = plugin.getHomesFor(player);
                for (String home : homes) {
                    if (home.toLowerCase().startsWith(currentInput)) {
                        suggestions.add(home);
                    }
                }
                if (suggestions.isEmpty()) {
                    suggestions.add("<home_name>");
                }
            }

            // /home admin <create/delete/seeplayer>
            if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                suggestions.add("create");
                suggestions.add("delete");
                suggestions.add("seeplayer");
            }

            // /home admin create <player>
            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                for (Player online : Bukkit.getOnlinePlayers()) suggestions.add(online.getName());
            }

            // /home admin create <player> <n>
            if (args.length >= 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                suggestions.add("<home_name>");
            }

            // /home admin delete <player>
            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
                for (Player online : Bukkit.getOnlinePlayers()) suggestions.add(online.getName());
            }

            // /home admin delete <player> <n>  — suggest homes milik player target
            if (args.length >= 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target != null) {
                    String currentInput = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).toLowerCase();
                    List<String> homes = plugin.getHomesFor(target);
                    for (String home : homes) {
                        if (home.toLowerCase().startsWith(currentInput)) {
                            suggestions.add(home);
                        }
                    }
                } else {
                    suggestions.add("<home_name>");
                }
            }

            // /home admin seeplayer <player>
            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("seeplayer")) {
                for (Player online : Bukkit.getOnlinePlayers()) suggestions.add(online.getName());
            }
        }

        return suggestions;
    }
}