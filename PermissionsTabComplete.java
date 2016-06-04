package me.bursty.ranks.main;

import com.google.common.collect.ImmutableList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.permissions.Permission;
import org.bukkit.util.StringUtil;

import java.util.*;

final class PermissionsTabComplete implements TabCompleter {

    private final List<String> BOOLEAN = ImmutableList.of("true", "false");
    private final List<String> ROOT_SUBS = ImmutableList.of("reload", "about", "check", "info", "dump", "rank", "setrank", "group", "player");
    private final List<String> GROUP_SUBS = ImmutableList.of("list", "players", "setperm", "unsetperm");
    private final List<String> PLAYER_SUBS = ImmutableList.of("setgroup", "addgroup", "removegroup", "setperm", "unsetperm");

    private final HashSet<Permission> permSet = new HashSet<Permission>();
    private final ArrayList<String> permList = new ArrayList<String>();

    private final PermissionsPlugin plugin;

    public PermissionsTabComplete(PermissionsPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {

        String lastArg = args[args.length - 1];

        if (args.length <= 1) {
            return partial(args[0], ROOT_SUBS);
        } else if (args.length == 2) {
            String sub = args[0];
            if (sub.equals("check")) {
                return partial(lastArg, allNodes());
            } else if (sub.equals("info")) {
                return partial(lastArg, allNodes());
            } else if (sub.equals("dump")) {
                return null;
            } else if (sub.equals("rank") || sub.equals("setrank")) {
                return null;
            } else if (sub.equals("group")) {
                return partial(lastArg, GROUP_SUBS);
            } else if (sub.equals("player")) {
                return partial(lastArg, PLAYER_SUBS);
            }
        } else {
            String sub = args[0];
            if (sub.equals("check") && args.length == 3) {
                return null;
            } else if ((sub.equals("rank") || sub.equals("setrank")) && args.length == 3) {
                return partial(lastArg, allGroups());
            } else if (sub.equals("group")) {
                return groupComplete(sender, args);
            } else if (sub.equals("player")) {
                return playerComplete(sender, args);
            }
        }

        return ImmutableList.of();
    }

    private List<String> groupComplete(CommandSender sender, String[] args) {
        String sub = args[1];
        String lastArg = args[args.length - 1];

        if (sub.equals("players")) {
            if (args.length == 3) {
                return partial(lastArg, allGroups());
            }
        } else if (sub.equals("setperm")) {
            if (args.length == 3) {
                return partial(lastArg, allGroups());
            } else if (args.length == 4) {
                return worldNodeComplete(lastArg);
            } else if (args.length == 5) {
                return partial(lastArg, BOOLEAN);
            }
        } else if (sub.equals("unsetperm")) {
            if (args.length == 3) {
                return partial(lastArg, allGroups());
            } else if (args.length == 4) {
                return worldNodeComplete(lastArg);
            }
        }

        return ImmutableList.of();
    }

    private List<String> playerComplete(CommandSender sender, String[] args) {
        String sub = args[1];
        String lastArg = args[args.length - 1];
        final List<String> players = null;

        if (sub.equals("groups")) {
            if (args.length == 3) {
                return players;
            }
        } else if (sub.equals("setgroup")) {
            if (args.length == 3) {
                return players;
            } else if (args.length == 4) {
                int idx = lastArg.lastIndexOf(',');
                if (idx == -1) {
                    return partial(lastArg, allGroups());
                } else {
                    String done = lastArg.substring(0, idx + 1);
                    String toComplete = lastArg.substring(idx + 1);
                    List<String> groups = partial(toComplete, allGroups());
                    List<String> result = new ArrayList<String>(groups.size());
                    for (String group : groups) {
                        result.add(done + group);
                    }
                    return result;
                }
            }
        } else if (sub.equals("addgroup") || sub.equals("removegroup")) {
            if (args.length == 3) {
                return players;
            } else if (args.length == 4) {
                return partial(lastArg, allGroups());
            }
        } else if (sub.equals("setperm")) {
            if (args.length == 3) {
                return players;
            } else if (args.length == 4) {
                return worldNodeComplete(lastArg);
            } else if (args.length == 5) {
                return partial(lastArg, BOOLEAN);
            }
        } else if (sub.equals("unsetperm")) {
            if (args.length == 3) {
                return players;
            } else if (args.length == 4) {
                return worldNodeComplete(lastArg);
            }
        }

        return ImmutableList.of();
    }

    private Collection<String> allGroups() {
        return plugin.getConfig().getConfigurationSection("groups").getKeys(false);
    }

    private Collection<String> allNodes() {
        Set<Permission> newPermSet = plugin.getServer().getPluginManager().getPermissions();
        if (!permSet.equals(newPermSet)) {
            permSet.clear();
            permSet.addAll(newPermSet);

            permList.clear();
            for (Permission p : permSet) {
                permList.add(p.getName());
            }
            Collections.sort(permList);
        }
        return permList;
    }

    private List<String> worldNodeComplete(String token) {
        return partial(token, allNodes());
    }

    private List<String> partial(String token, Collection<String> from) {
        return StringUtil.copyPartialMatches(token, from, new ArrayList<String>(from.size()));
    }

}