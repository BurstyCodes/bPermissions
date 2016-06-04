package me.bursty.ranks.main;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

final class PermissionsCommand implements CommandExecutor {

    private final PermissionsPlugin plugin;

    public PermissionsCommand(PermissionsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
        if (split.length < 1) {
            return !checkPerm(sender, "help") || usage(sender, command);
        }

        String subcommand = split[0];
        if (subcommand.equals("reload")) {
            if (!checkPerm(sender, "reload")) return true;
            plugin.reloadConfig();
            if (plugin.configLoadError) {
                plugin.configLoadError = false;
                sender.sendMessage("§7>> §cYour configuration is invalid, see the console for details.");
            } else {
                plugin.refreshPermissions();
                sender.sendMessage("§7>> §aConfiguration reloaded.");
            }
            return true;
        } else if (subcommand.equals("about")) {
            if (!checkPerm(sender, "about")) return true;
            
            sender.sendMessage("§7>> §bPlugin coded and developed by Bursty.");
            sender.sendMessage("§7>> §3Coded for The Raging Panter.");

            return true;
        } else if (subcommand.equals("check")) {
            if (!checkPerm(sender, "check")) return true;
            if (split.length != 2 && split.length != 3) return usage(sender, command, subcommand);

            String node = split[1];
            Permissible permissible;
            if (split.length == 2) {
                permissible = sender;
            } else {
                permissible = plugin.getServer().getPlayer(split[2]);
            }

            String name = (permissible instanceof Player) ? ((Player) permissible).getName() : (permissible instanceof ConsoleCommandSender) ? "Console" : "Unknown";

            if (permissible == null) {
                sender.sendMessage("§7>> §cPlayer " + ChatColor.GRAY + split[2] + "§c not found.");
            } else {
                boolean set = permissible.isPermissionSet(node), has = permissible.hasPermission(node);
                String sets = set ? " sets " : " defaults ";
                String perm = has ? "true" : "false";
                sender.sendMessage("§7>> §3Player " + ChatColor.GRAY + name + "§3" + sets + ChatColor.GRAY + node + "§3" + " to " + ChatColor.GRAY + perm + "§3" + ".");
            }
            return true;
        } else if (subcommand.equals("info")) {
            if (!checkPerm(sender, "info")) return true;
            if (split.length != 2) return usage(sender, command, subcommand);

            String node = split[1];
            Permission perm = plugin.getServer().getPluginManager().getPermission(node);

            if (perm == null) {
                sender.sendMessage("§7>> §cPermission §7" + node + "§c not found.");
            } else {
                sender.sendMessage("§7>> §3Info on permission " + "§b" + perm.getName() + "§7:");
                sender.sendMessage("§7>> §3Default: " + "§b" + perm.getDefault());
                if (perm.getDescription() != null && perm.getDescription().length() > 0) {
                    sender.sendMessage("§7>> §3Description: §b" + perm.getDescription());
                }
                if (perm.getChildren() != null && perm.getChildren().size() > 0) {
                    sender.sendMessage("§7>> §bChildren: §b" + perm.getChildren().size());
                }
                if (perm.getPermissibles() != null && perm.getPermissibles().size() > 0) {
                    int num = 0, numTrue = 0;
                    for (Permissible who : perm.getPermissibles()) {
                        ++num;
                        if (who.hasPermission(perm)) {
                            ++numTrue;
                        }
                    }
                    sender.sendMessage("§7>> §3Set on: " + "§b" + num + "§7" + " (" + "§b" + numTrue + " true" + "§7)");
                }
            }
            return true;
        } else if (subcommand.equals("dump")) {
            if (!checkPerm(sender, "dump")) return true;
            if (split.length < 1 || split.length > 3) return usage(sender, command, subcommand);

            int page;
            Permissible permissible;
            if (split.length == 1) {
                permissible = sender;
                page = 1;
            } else if (split.length == 2) {
                permissible = sender;
                try {
                    page = Integer.parseInt(split[1]);
                } catch (NumberFormatException ex) {
                    if (split[1].equalsIgnoreCase("-file")) {
                        page = -1;
                    } else {
                        permissible = plugin.getServer().getPlayer(split[1]);
                        page = 1;
                    }
                }
            } else {
                permissible = plugin.getServer().getPlayer(split[1]);
                try {
                    page = Integer.parseInt(split[2]);
                } catch (NumberFormatException ex) {
                    if (split[2].equalsIgnoreCase("-file")) {
                        page = -1;
                    } else {
                        page = 1;
                    }
                }
            }

            if (permissible == null) {
                sender.sendMessage("§7>> §3Player " + ChatColor.GRAY + split[1] + "§3" + " not found.");
                return true;
            }

            ArrayList<PermissionAttachmentInfo> dump = new ArrayList<PermissionAttachmentInfo>(permissible.getEffectivePermissions());
            Collections.sort(dump, new Comparator<PermissionAttachmentInfo>() {
                public int compare(PermissionAttachmentInfo a, PermissionAttachmentInfo b) {
                    return a.getPermission().compareTo(b.getPermission());
                }
            });

            if (page == -1) {
                File file = new File(plugin.getDataFolder(), "dump.txt");
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    PrintStream out = new PrintStream(fos);
                    out.println("PermissionsBukkit dump for: " + ((CommandSender) permissible).getName());
                    out.println(new Date().toString());

                    for (PermissionAttachmentInfo info : dump) {
                        if (info.getAttachment() == null) {
                            out.println(info.getPermission() + "=" + info.getValue() + " (default)");
                        } else {
                            out.println(info.getPermission() + "=" + info.getValue() + " (" + info.getAttachment().getPlugin().getDescription().getName() + ")");
                        }
                    }

                    out.close();
                    fos.close();

                    sender.sendMessage("§7>> §3Permissions dump written to " + ChatColor.GRAY + file);
                } catch (IOException e) {
                    sender.sendMessage("§7>> §3Failed to write to dump.txt, see the console for more details");
                    sender.sendMessage("§7>> §3" +  e.toString());
                    e.printStackTrace();
                }
                return true;
            }

            int numpages = 1 + (dump.size() - 1) / 8;
            if (page > numpages) {
                page = numpages;
            } else if (page < 1) {
                page = 1;
            }


            int start = 8 * (page - 1);
            sender.sendMessage(ChatColor.GRAY + "[==== " + "§3" + "Page " + page + " of " + numpages + ChatColor.GRAY + " ====]");
            for (int i = start; i < start + 8 && i < dump.size(); ++i) {
                PermissionAttachmentInfo info = dump.get(i);

                if (info.getAttachment() == null) {
                    sender.sendMessage("§7>> §3" + "Node " + "§7" + info.getPermission() + "§3" + "=" + "§7" + info.getValue() + "§3" + " (" + "§b" + "default" + "§3" + ")");
                } else {
                    sender.sendMessage("§7>> §3" + "Node " + "§7" + info.getPermission() + "§3" + "=" + "§7" + info.getValue() + "§3" + " (" + "§7" + info.getAttachment().getPlugin().getDescription().getName() + "§3" + ")");
                }
            }
            return true;
        } else if (subcommand.equals("rank") || subcommand.equals("setrank")) {
            if (!checkPerm(sender, "setrank")) return true;
            if (split.length != 3) return usage(sender, command, subcommand);

            // This is essentially player setgroup with an added check
            UUID player = resolvePlayer(sender, split[1]);
            if (player == null) return true;
            String group = split[2];

            if (!sender.hasPermission("permissions.setrank." + group)) {
                sender.sendMessage("§7>> §cYou do not have permission to add players to " + ChatColor.GRAY + group + ChatColor.RED + ".");
                return true;
            }

            plugin.createNode("users/" + player).set("groups", Arrays.asList(group));
            plugin.refreshForPlayer(player);

            sender.sendMessage("§7>> §3Player " + ChatColor.GRAY + player + " §3 " + " is now in " + ChatColor.GRAY + group + "§3" + ".");
            return true;
        } else if (subcommand.equals("group")) {
            if (split.length < 2) {
                return !checkPerm(sender, "group.help") || usage(sender, command, subcommand);
            }
            groupCommand(sender, command, split);
            return true;
        } else if (subcommand.equals("player")) {
            if (split.length < 2) {
                return !checkPerm(sender, "player.help") || usage(sender, command, subcommand);
            }
            playerCommand(sender, command, split);
            return true;
        } else {
            return !checkPerm(sender, "help") || usage(sender, command);
        }
    }

    private boolean groupCommand(CommandSender sender, Command command, String[] split) {
        String subcommand = split[1];

        if (subcommand.equals("list")) {
            if (!checkPerm(sender, "group.list")) return true;
            if (split.length != 2) return usage(sender, command, "group list");

            String result = "", sep = "";
            for (String key : plugin.getNode("groups").getKeys(false)) {
                result += sep + key;
                sep = ", ";
            }
            sender.sendMessage("§3" + "Groups: " + "§7" + result);
            return true;
        } else if (subcommand.equals("players")) {
            if (!checkPerm(sender, "group.players")) return true;
            if (split.length != 3) return usage(sender, command, "group players");
            String group = split[2];

            if (plugin.getNode("groups/" + group) == null) {
                sender.sendMessage("§7>> §3No such group " + ChatColor.GRAY + group + "§3"+ ".");
                return true;
            }

            List<String> users = new LinkedList<String>();
            for (String userKey : plugin.getNode("users").getKeys(false)) {
                ConfigurationSection node = plugin.getNode("users/" + userKey);
                if (node.getStringList("groups").contains(group)) {
                    try {
                        // show UUID and name if available
                        UUID uuid = UUID.fromString(userKey);
                        String name = node.getString("name", "???");
                        users.add(name + "§3" + " (" + "§7" + uuid + "§3" + ")");
                    } catch (IllegalArgumentException ex) {
                        users.add(userKey + "§3" + " (" + "§7" + "unconverted" + "§3" + ")");
                    }
                }
            }
            sender.sendMessage("§7>> §3" + "Users in " + "§7" + group + "§3" + " (" + "§7" + users.size() + "§3" + "):");
            for (String user : users) {
                sender.sendMessage("  " + user);
            }
            return true;
        } else if (subcommand.equals("setperm")) {
            if (!checkPerm(sender, "group.setperm")) return true;
            if (split.length != 4 && split.length != 5) return usage(sender, command, "group setperm");
            String group = split[2];
            String perm = split[3];
            boolean value = (split.length != 5) || Boolean.parseBoolean(split[4]);

            String node = "permissions";
            if (plugin.getNode("groups/" + group) == null) {
                sender.sendMessage(ChatColor.RED + "No such group " + "§7" + group + ChatColor.RED + ".");
                return true;
            }

            if (perm.contains(":")) {
                String world = perm.substring(0, perm.indexOf(':'));
                perm = perm.substring(perm.indexOf(':') + 1);
                node = "worlds/" + world;
            }

            plugin.createNode("groups/" + group + "/" + node).set(perm, value);
            plugin.refreshForGroup(group);

            sender.sendMessage("§7>> §3" + "Group " + "§7" + group + "§3" + " now has " + "§7" + perm + "§3" + " = " + "§7" + value + "§3" + ".");
            return true;
        } else if (subcommand.equals("unsetperm")) {
            if (!checkPerm(sender, "group.unsetperm")) return true;
            if (split.length != 4) return usage(sender, command, "group unsetperm");
            String group = split[2].toLowerCase();
            String perm = split[3];

            String node = "permissions";
            if (plugin.getNode("groups/" + group) == null) {
                sender.sendMessage(ChatColor.RED + "No such group " + "§7" + group + ChatColor.RED + ".");
                return true;
            }

            if (perm.contains(":")) {
                String world = perm.substring(0, perm.indexOf(':'));
                perm = perm.substring(perm.indexOf(':') + 1);
                node = "worlds/" + world;
            }

            ConfigurationSection sec = plugin.createNode("groups/" + group + "/" + node);
            if (!sec.contains(perm)) {
                sender.sendMessage("§7>> §3" + "Group " + "§7" + group + "§3" + " did not have " + "§7" + perm + "§3" + " set.");
                return true;
            }
            sec.set(perm, null);
            plugin.refreshForGroup(group);

            sender.sendMessage("§7>> §3" + "Group " + "§7" + group + "§3" + " no longer has " + "§7" + perm + "§3" + " set.");
            return true;
        } else {
            return !checkPerm(sender, "group.help") || usage(sender, command);
        }
    }

    private boolean playerCommand(CommandSender sender, Command command, String[] split) {
        String subcommand = split[1];

        if (subcommand.equals("groups")) {
            if (!checkPerm(sender, "player.groups")) return true;
            if (split.length != 3) return usage(sender, command, "player groups");
            UUID player = resolvePlayer(sender, split[2]);
            if (player == null) return true;

            if (plugin.getNode("users/" + player) == null) {
                sender.sendMessage("§3" + "Player " + "§7" + player + ChatColor.RED + " is in the default group.");
                return true;
            }

            int count = 0;
            String text = "", sep = "";
            for (String group : plugin.getNode("users/" + player).getStringList("groups")) {
                ++count;
                text += sep + group;
                sep = ", ";
            }
            sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " is in groups (" + "§7" + count + "§3" + "): " + "§7" + text);
            return true;
        } else if (subcommand.equals("setgroup")) {
            if (!checkPerm(sender, "player.setgroup")) return true;
            if (split.length != 4) return usage(sender, command, "player setgroup");
            UUID player = resolvePlayer(sender, split[2]);
            if (player == null) return true;
            String[] groups = split[3].split(",");

            plugin.createNode("users/" + player).set("groups", Arrays.asList(groups));
            plugin.refreshForPlayer(player);

            sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " is now in " + "§7" + split[3] + "§3" + ".");
            return true;
        } else if (subcommand.equals("addgroup")) {
            if (!checkPerm(sender, "player.addgroup")) return true;
            if (split.length != 4) return usage(sender, command, "player addgroup");
            UUID player = resolvePlayer(sender, split[2]);
            if (player == null) return true;
            String group = split[3];

            List<String> list = plugin.createNode("users/" + player).getStringList("groups");
            if (list.contains(group)) {
                sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " was already in " + "§7" + group + "§3" + ".");
                return true;
            }
            list.add(group);
            plugin.getNode("users/" + player).set("groups", list);

            plugin.refreshForPlayer(player);

            sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " is now in " + "§7" + group + "§3" + ".");
            return true;
        } else if (subcommand.equals("removegroup")) {
            if (!checkPerm(sender, "player.removegroup")) return true;
            if (split.length != 4) return usage(sender, command, "player removegroup");
            UUID player = resolvePlayer(sender, split[2]);
            if (player == null) return true;
            String group = split[3];

            List<String> list = plugin.createNode("users/" + player).getStringList("groups");
            if (!list.contains(group)) {
                sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " was not in " + "§7" + group + "§3" + ".");
                return true;
            }
            list.remove(group);
            plugin.getNode("users/" + player).set("groups", list);

            plugin.refreshForPlayer(player);

            sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " is no longer in " + "§7" + group + "§3" + ".");
            return true;
        } else if (subcommand.equals("setperm")) {
            if (!checkPerm(sender, "player.setperm")) return true;
            if (split.length != 4 && split.length != 5) return usage(sender, command, "player setperm");
            UUID player = resolvePlayer(sender, split[2]);
            if (player == null) return true;
            String perm = split[3];
            boolean value = (split.length != 5) || Boolean.parseBoolean(split[4]);

            String node = "permissions";
            if (perm.contains(":")) {
                String world = perm.substring(0, perm.indexOf(':'));
                perm = perm.substring(perm.indexOf(':') + 1);
                node = "worlds/" + world;
            }

            plugin.createNode("users/" + player + "/" + node).set(perm, value);
            plugin.refreshForPlayer(player);

            sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " now has " + "§7" + perm + "§3" + " = " + "§7" + value + "§3" + ".");
            return true;
        } else if (subcommand.equals("unsetperm")) {
            if (!checkPerm(sender, "player.unsetperm")) return true;
            if (split.length != 4) return usage(sender, command, "player unsetperm");
            UUID player = resolvePlayer(sender, split[2]);
            if (player == null) return true;
            String perm = split[3];

            String node = "permissions";
            if (perm.contains(":")) {
                String world = perm.substring(0, perm.indexOf(':'));
                perm = perm.substring(perm.indexOf(':') + 1);
                node = "worlds/" + world;
            }

            ConfigurationSection sec = plugin.createNode("users/" + player + "/" + node);
            if (!sec.contains(perm)) {
                sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " did not have " + "§7" + perm + "§3" + " set.");
                return true;
            }
            sec.set(perm, null);
            plugin.refreshForPlayer(player);

            sender.sendMessage("§7>> §3" + "Player " + "§7" + player + "§3" + " no longer has " + "§7" + perm + "§3" + " set.");
            return true;
        } else {
            return !checkPerm(sender, "player.help") || usage(sender, command);
        }
    }

    private UUID resolvePlayer(CommandSender sender, String arg) {
        arg = arg.toLowerCase();

        List<Player> players = plugin.getServer().matchPlayer(arg);
        if (players.size() == 1) {
            return players.get(0).getUniqueId();
        } else if (players.size() > 1) {
            sender.sendMessage(ChatColor.RED + "Username " + "§7" + arg + ChatColor.RED + " is ambiguous.");
            return null;
        }

        if (arg.length() == 32) {
            arg = arg.substring(0, 8) + "-" + arg.substring(8, 12) + "-" + arg.substring(12, 16) +
                    "-" + arg.substring(16, 20) + "-" + arg.substring(20, 32);
        }
        if (arg.length() == 36) {
            try {
                return UUID.fromString(arg);
            } catch (IllegalArgumentException ex) {
            }
        }

        sender.sendMessage("§7>> " + ChatColor.RED + "Could not resolve player: " + "§7" + arg);
        sender.sendMessage("§7>> " + ChatColor.RED + "You must provide a UUID or the name of an online player.");
        return null;
    }


    private boolean checkPerm(CommandSender sender, String subnode) {
        boolean ok = sender.hasPermission("permissions." + subnode);
        if (!ok) {
            sender.sendMessage("§7>> " + ChatColor.RED + "You do not have permissions to do that.");
        }
        return ok;
    }

    private boolean usage(CommandSender sender, Command command) {
        sender.sendMessage(ChatColor.GRAY + "[====" + "§3" + " /permissons " + ChatColor.GRAY + "====]");
        for (String line : command.getUsage().split("\\n")) {
            if ((line.startsWith("/<command> group") && !line.startsWith("/<command> group -")) ||
                    (line.startsWith("/<command> player") && !line.startsWith("/<command> player -"))) {
                continue;
            }
            sender.sendMessage(formatLine(line));
        }
        return true;
    }

    private boolean usage(CommandSender sender, Command command, String subcommand) {
        sender.sendMessage(ChatColor.GRAY + "[====" + "§3" + " /permissons " + subcommand + " " + ChatColor.GRAY + "====]");
        for (String line : command.getUsage().split("\\n")) {
            if (line.startsWith("/<command> " + subcommand)) {
                sender.sendMessage(formatLine(line));
            }
        }
        return true;
    }

    private String formatLine(String line) {
        int i = line.indexOf(" - ");
        String usage = line.substring(0, i);
        String desc = line.substring(i + 3);

        usage = usage.replace("<command>", "permissions");
        usage = usage.replaceAll("\\[[^]:]+\\]", ChatColor.AQUA + "$0" + "§3");
        usage = usage.replaceAll("\\[[^]]+:\\]", ChatColor.AQUA + "$0" + "§b");
        usage = usage.replaceAll("<[^>]+>", "§b" + "$0" + "§3");

        return "§3" + usage + " - " + "§7" + desc;
    }

}