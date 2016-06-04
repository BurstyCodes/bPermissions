package me.bursty.ranks.main;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PermissionsPlugin extends JavaPlugin {

    private final PlayerListener playerListener = new PlayerListener(this);
    private final PermissionsCommand commandExecutor = new PermissionsCommand(this);
    private final PermissionsTabComplete tabCompleter = new PermissionsTabComplete(this);
    private final PermissionsMetrics metrics = new PermissionsMetrics(this);

    private final HashMap<UUID, PermissionAttachment> permissions = new HashMap<UUID, PermissionAttachment>();

    private File configFile;
    private YamlConfiguration config;

    public boolean configLoadError = false;

    @SuppressWarnings("deprecation")
	@Override
    public void onEnable() {
        configFile = new File(getDataFolder(), "config.yml");
        saveDefaultConfig();
        reloadConfig();

        getCommand("permissions").setExecutor(commandExecutor);
        getCommand("permissions").setTabCompleter(tabCompleter);
        getServer().getPluginManager().registerEvents(playerListener, this);

        for (Player p : getServer().getOnlinePlayers()) {
            registerPlayer(p);
        }

        try {
            metrics.start();
        } catch (IOException ex) {
            getLogger().warning("Failed to connect to plugin metrics: " + ex.getMessage());
        }

		int count = getServer().getOnlinePlayers().length;
        if (count > 0) {
            getLogger().info("Enabled successfully, " + count + " online players registered");
        } else {
            getLogger().info("Enabled successfully");
        }
    }

    @Override
    public FileConfiguration getConfig() {
        return config;
    }

    @Override
    public void reloadConfig() {
        config = new YamlConfiguration();
        config.options().pathSeparator('/');
        try {
            config.load(configFile);
        } catch (InvalidConfigurationException ex) {
            configLoadError = true;

            ArrayList<String> lines = new ArrayList<String>();
            Pattern pattern = Pattern.compile("line (\\d+), column");
            Matcher matcher = pattern.matcher(ex.getMessage());
            while (matcher.find()) {
                String lineNo = matcher.group(1);
                if (!lines.contains(lineNo)) {
                    lines.add(lineNo);
                }
            }

            String msg = "Your configuration is invalid! ";
            if (lines.size() == 0) {
                msg += "Unable to find any line numbers.";
            } else {
                msg += "Take a look at line(s): " + lines.get(0);
                for (String lineNo : lines.subList(1, lines.size())) {
                    msg += ", " + lineNo;
                }
            }
            getLogger().severe(msg);
            
            try {
                File outFile = new File(getDataFolder(), "config_error.txt");
                PrintStream out = new PrintStream(new FileOutputStream(outFile));
                out.println();
                out.println(ex.toString());
                out.close();
                getLogger().info("Saved the full error message to " + outFile);
            } catch (IOException ex2) {
                getLogger().severe("Failed to save the full error message!");
            }

            File backupFile = new File(getDataFolder(), "config_backup.yml");
            File sourceFile = new File(getDataFolder(), "config.yml");
            if (FileUtil.copy(sourceFile, backupFile)) {
                getLogger().info("Saved a backup of your configuration to " + backupFile);
            } else {
                getLogger().severe("Failed to save a configuration backup!");
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to load configuration", ex);
        }
    }

    @Override
    public void saveConfig() {
        if (config.getKeys(true).size() > 0) {
            try {
                config.save(configFile);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Failed to save configuration", ex);
            }
        }
    }

    @SuppressWarnings("deprecation")
	@Override
    public void onDisable() {
        for (Player p : getServer().getOnlinePlayers()) {
            unregisterPlayer(p);
        }

        int count = getServer().getOnlinePlayers().length;
        if (count > 0) {
            getLogger().info("Disabled successfully, " + count + " online players unregistered");
        }
    }

    public Group getGroup(String groupName) {
        metrics.apiUsed();
        if (getNode("groups") != null) {
            for (String key : getNode("groups").getKeys(false)) {
                if (key.equalsIgnoreCase(groupName)) {
                    return new Group(this, key);
                }
            }
        }
        return null;
    }

    @Deprecated
    public List<Group> getGroups(String playerName) {
        metrics.apiUsed();
        ArrayList<Group> result = new ArrayList<Group>();
        ConfigurationSection node = getUsernameNode(playerName);
        if (node != null) {
            for (String key : node.getStringList("groups")) {
                result.add(new Group(this, key));
            }
        } else {
            result.add(new Group(this, "default"));
        }
        return result;
    }

    public List<Group> getGroups(UUID player) {
        metrics.apiUsed();
        ArrayList<Group> result = new ArrayList<Group>();
        if (getNode("users/" + player) != null) {
            for (String key : getNode("users/" + player).getStringList("groups")) {
                result.add(new Group(this, key));
            }
        } else {
            result.add(new Group(this, "default"));
        }
        return result;
    }

    @Deprecated
    public PermissionInfo getPlayerInfo(String playerName) {
        metrics.apiUsed();
        ConfigurationSection node = getUsernameNode(playerName);
        if (node == null) {
            return null;
        } else {
            return new PermissionInfo(this, node, "groups");
        }
    }


    public PermissionInfo getPlayerInfo(UUID player) {
        metrics.apiUsed();
        if (getNode("users/" + player) == null) {
            return null;
        } else {
            return new PermissionInfo(this, getNode("users/" + player), "groups");
        }
    }
    public List<Group> getAllGroups() {
        metrics.apiUsed();
        ArrayList<Group> result = new ArrayList<Group>();
        if (getNode("groups") != null) {
            for (String key : getNode("groups").getKeys(false)) {
                result.add(new Group(this, key));
            }
        }
        return result;
    }

    protected PermissionsMetrics getMetrics() {
        return metrics;
    }

    protected void registerPlayer(Player player) {
        if (permissions.containsKey(player.getUniqueId())) {
            debug("Registering " + player.getName() + ": was already registered");
            unregisterPlayer(player);
        }
        PermissionAttachment attachment = player.addAttachment(this);
        permissions.put(player.getUniqueId(), attachment);
        calculateAttachment(player);
    }

    protected void unregisterPlayer(Player player) {
        if (permissions.containsKey(player.getUniqueId())) {
            try {
                player.removeAttachment(permissions.get(player.getUniqueId()));
            } catch (IllegalArgumentException ex) {
                debug("Unregistering " + player.getName() + ": player did not have attachment");
            }
            permissions.remove(player.getUniqueId());
        } else {
            debug("Unregistering " + player.getName() + ": was not registered");
        }
    }

    protected void refreshForPlayer(UUID player) {
        saveConfig();
        debug("Refreshing for player " + player);

        Player onlinePlayer = getServer().getPlayer(player);
        if (onlinePlayer != null) {
            calculateAttachment(onlinePlayer);
        }
    }

    private void fillChildGroups(HashSet<String> childGroups, String group) {
        if (childGroups.contains(group)) return;
        childGroups.add(group);

        for (String key : getNode("groups").getKeys(false)) {
            for (String parent : getNode("groups/" + key).getStringList("inheritance")) {
                if (parent.equalsIgnoreCase(group)) {
                    fillChildGroups(childGroups, key);
                }
            }
        }
    }

    protected void refreshForGroup(String group) {
        saveConfig();

        HashSet<String> childGroups = new HashSet<String>();
        fillChildGroups(childGroups, group);
        debug("Refreshing for group " + group + " (total " + childGroups.size() + " subgroups)");

        for (UUID uuid : permissions.keySet()) {
            Player player = getServer().getPlayer(uuid);
            ConfigurationSection node = getUserNode(player);

            List<String> groupList = (node != null) ? node.getStringList("groups") : Arrays.asList("default");
            for (String userGroup : groupList) {
                if (childGroups.contains(userGroup)) {
                    calculateAttachment(player);
                    break;
                }
            }
        }
    }

    protected void refreshPermissions() {
        debug("Refreshing all permissions (for " + permissions.size() + " players)");
        for (UUID player : permissions.keySet()) {
            calculateAttachment(getServer().getPlayer(player));
        }
    }

    protected ConfigurationSection getNode(String node) {
        for (String entry : getConfig().getKeys(true)) {
            if (node.equalsIgnoreCase(entry) && getConfig().isConfigurationSection(entry)) {
                return getConfig().getConfigurationSection(entry);
            }
        }
        return null;
    }

    protected ConfigurationSection getUserNode(Player player) {
        ConfigurationSection sec = getNode("users/" + player.getUniqueId());
        if (sec == null) {
            sec = getNode("users/" + player.getName());
            if (sec != null) {
                getConfig().set(sec.getCurrentPath(), null);
                getConfig().set("users/" + player.getUniqueId(), sec);
                sec.set("name", player.getName());
                debug("Migrated " + player.getName() + " to UUID " + player.getUniqueId());
                saveConfig();
            }
        }

        if (sec != null) {
            if (!player.getName().equals(sec.getString("name"))) {
                debug("Updating name of " + player.getUniqueId() + " to: " + player.getName());
                sec.set("name", player.getName());
                saveConfig();
            }
        }

        return sec;
    }

    protected ConfigurationSection getUsernameNode(String name) {
        ConfigurationSection sec = getNode("users");
        if (sec != null) {
            for (String child : sec.getKeys(false)) {
                ConfigurationSection node = sec.getConfigurationSection(child);
                if (node != null && (name.equals(node.getString("name")) || name.equals("child"))) {
                    return node;
                }
            }
        }
        return null;
    }

    protected ConfigurationSection createNode(String node) {
        ConfigurationSection sec = getConfig();
        for (String piece : node.split("/")) {
            ConfigurationSection sec2 = getNode(sec == getConfig() ? piece : sec.getCurrentPath() + "/" + piece);
            if (sec2 == null) {
                sec2 = sec.createSection(piece);
            }
            sec = sec2;
        }
        return sec;
    }

    protected HashMap<String, Boolean> getAllPerms(String desc, String path) {
        ConfigurationSection node = getNode(path);

        int failures = 0;
        String firstFailure = "";

        boolean fixed = false, fixedNow = true;
        while (fixedNow) {
            fixedNow = false;
            for (String key : node.getKeys(true)) {
                if (node.isBoolean(key) && key.contains("/")) {
                    node.set(key.replace("/", "."), node.getBoolean(key));
                    node.set(key, null);
                    fixed = fixedNow = true;
                } else if (node.isConfigurationSection(key) && node.getConfigurationSection(key).getKeys(true).size() == 0) {
                    node.set(key, null);
                    fixed = fixedNow = true;
                }
            }
        }
        if (fixed) {
            getLogger().info("Fixed broken nesting in " + desc + ".");
            saveConfig();
        }

        LinkedHashMap<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        for (String key : node.getKeys(false)) {
            if (node.isBoolean(key)) {
                result.put(key, node.getBoolean(key));
            } else {
                ++failures;
                if (firstFailure.length() == 0) {
                    firstFailure = key;
                }
            }
        }

        if (failures == 1) {
            getLogger().warning("In " + desc + ": " + firstFailure + " is non-boolean.");
        } else if (failures > 1) {
            getLogger().warning("In " + desc + ": " + firstFailure + " is non-boolean (+" + (failures - 1) + " more).");
        }

        return result;
    }

    protected void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Debug: " + message);
        }
    }

    protected void calculateAttachment(Player player) {
        if (player == null) {
            return;
        }
        PermissionAttachment attachment = permissions.get(player.getUniqueId());
        if (attachment == null) {
            debug("Calculating permissions on " + player.getName() + ": attachment was null");
            return;
        }

        Map<String, Boolean> values = calculatePlayerPermissions(player, player.getWorld().getName());

        Map<String, Boolean> dest = reflectMap(attachment);
        dest.clear();
        dest.putAll(values);
        debug("Calculated permissions on " + player.getName() + ": " + dest.size() + " values");

        player.recalculatePermissions();
    }

    private Field pField;

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> reflectMap(PermissionAttachment attachment) {
        try {
            if (pField == null) {
                pField = PermissionAttachment.class.getDeclaredField("permissions");
                pField.setAccessible(true);
            }
            return (Map<String, Boolean>) pField.get(attachment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <K, V> void put(Map<K, V> dest, K key, V value) {
        dest.remove(key);
        dest.put(key, value);
    }

    private <K, V> void putAll(Map<K, V> dest, Map<K, V> src) {
        for (Map.Entry<K, V> entry : src.entrySet()) {
            put(dest, entry.getKey(), entry.getValue());
        }
    }

    private Map<String, Boolean> calculatePlayerPermissions(Player player, String world) {
        ConfigurationSection node = getUserNode(player);
       
        if (node == null) {
            return calculateGroupPermissions("default", world);
        }

        String nodePath = node.getCurrentPath();
        Map<String, Boolean> perms = new LinkedHashMap<String, Boolean>();

        for (String group : node.getStringList("groups")) {
            putAll(perms, calculateGroupPermissions(group, world));
        }

        if (getNode(nodePath + "/permissions") != null) {
            putAll(perms, getAllPerms("user " + player, nodePath + "/permissions"));
        }

        if (getNode(nodePath + "/worlds/" + world) != null) {
            putAll(perms, getAllPerms("user " + player + " world " + world, nodePath + "/worlds/" + world));
        }

        return perms;
    }

    private Map<String, Boolean> calculateGroupPermissions(String group, String world) {
        return calculateGroupPermissions0(new HashSet<String>(), group, world);
    }

    private Map<String, Boolean> calculateGroupPermissions0(Set<String> recursionBuffer, String group, String world) {
        String groupNode = "groups/" + group;

        if (getNode(groupNode) == null) {
            return new LinkedHashMap<String, Boolean>();
        }

        recursionBuffer.add(group);
        Map<String, Boolean> perms = new LinkedHashMap<String, Boolean>();

        for (String parent : getNode(groupNode).getStringList("inheritance")) {
            if (recursionBuffer.contains(parent)) {
                getLogger().warning("In group " + group + ": recursive inheritance from " + parent);
                continue;
            }

            putAll(perms, calculateGroupPermissions0(recursionBuffer, parent, world));
        }

        if (getNode(groupNode + "/permissions") != null) {
            putAll(perms, getAllPerms("group " + group, groupNode + "/permissions"));
        }

        if (getNode(groupNode + "/worlds/" + world) != null) {
            putAll(perms, getAllPerms("group " + group + " world " + world, groupNode + "/worlds/" + world));
        }

        return perms;
    }

}