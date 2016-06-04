package me.bursty.ranks.main;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class PermissionInfo {

    private final PermissionsPlugin plugin;
    private final ConfigurationSection node;
    private final String groupType;

    PermissionInfo(PermissionsPlugin plugin, ConfigurationSection node, String groupType) {
        this.plugin = plugin;
        this.node = node;
        this.groupType = groupType;
    }

    public List<Group> getGroups() {
        ArrayList<Group> result = new ArrayList<Group>();

        for (String key : node.getStringList(groupType)) {
            Group group = plugin.getGroup(key);
            if (group != null) {
                result.add(group);
            }
        }

        return result;
    }

    public Map<String, Boolean> getPermissions() {
        return plugin.getAllPerms(node.getName(), node.getCurrentPath());
    }

    public Set<String> getWorlds() {
        if (node.getConfigurationSection("worlds") == null) {
            return new HashSet<String>();
        }
        return node.getConfigurationSection("worlds").getKeys(false);
    }

    public Map<String, Boolean> getWorldPermissions(String world) {
        return plugin.getAllPerms(node.getName() + ":" + world, node.getName() + "/world/" + world);
    }

}