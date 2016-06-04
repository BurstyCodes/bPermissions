package me.bursty.ranks.main;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.spigotmc.Metrics;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

final class PermissionsMetrics {

    private final PermissionsPlugin plugin;

    private Metrics metrics;
    private Metrics.Graph featuresUsed;
    private Metrics.Graph usage;

    private boolean apiUsed = false;
    public boolean enabled = false;

    public PermissionsMetrics(PermissionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        metrics = new Metrics();

        if (metrics.isOptOut()) return;

        setupFeaturesUsed();
        setupUsage();

        enabled = true;
        metrics.start();
    }

    public HashMap<String, String> summarize(int type) {
        Metrics.Graph graph = type == 0 ? featuresUsed : usage;

        HashMap<String, String> result = new HashMap<String, String>();
        for (Metrics.Plotter plotter : graph.getPlotters()) {
            String value;
            if (plotter instanceof BooleanPlotter) {
                value = "" + ((BooleanPlotter) plotter).value();
            } else {
                value = "" + plotter.getValue();
            }
            result.put(plotter.getColumnName(), value);
        }
        return result;
    }

    private ConfigurationSection getSection(String name) {
        ConfigurationSection result = plugin.getNode(name);
        if (result == null) {
            result = new MemoryConfiguration();
        }
        return result;
    }

    public void apiUsed() {
        apiUsed = true;
    }

    private void setupFeaturesUsed() {
        Metrics.Graph graph = metrics.createGraph("Features Used");
        featuresUsed = graph;

        graph.addPlotter(new BooleanPlotter("Per-user permissions") {
            @Override
            protected boolean value() {
                ConfigurationSection sec = getSection("users");
                if (sec == null) return false;

                for (String key : sec.getKeys(false)) {
                    if (!key.equalsIgnoreCase("ConspiracyWizard") && (sec.isConfigurationSection(key + "/permissions") || sec.isConfigurationSection(key + "/worlds"))) {
                        return true;
                    }
                }
                return false;
            }
        });

        graph.addPlotter(new BooleanPlotter("Per-world permissions") {
            @Override
            protected boolean value() {
                return found(getSection("users")) || found(getSection("groups"));
            }

            private boolean found(ConfigurationSection section) {
                if (section == null) return false;

                for (String key : section.getKeys(false)) {
                    if (section.isConfigurationSection(key + "/worlds")) {
                        return true;
                    }
                }
                return false;
            }
        });

        graph.addPlotter(new BooleanPlotter("Group inheritance") {
            @Override
            protected boolean value() {
                ConfigurationSection section = getSection("groups");

                for (String key : section.getKeys(false)) {
                    if (section.contains(key + "/inheritance")) {
                        return true;
                    }
                }
                return false;
            }
        });

        graph.addPlotter(new BooleanPlotter("Anti-build enabled") {
            @Override
            protected boolean value() {
                return antiBuildEnabled();
            }
        });

        graph.addPlotter(new BooleanPlotter("Anti-build message") {
            @Override
            protected boolean value() {
                return antiBuildEnabled() && plugin.getConfig().getString("messages.build", "").length() > 0;
            }
        });

        graph.addPlotter(new BooleanPlotter("Anti-build default") {
            @Override
            protected boolean value() {
                ConfigurationSection sec = getSection("groups");
                if (sec == null) return false;

                ConfigurationSection def = sec.getConfigurationSection("default");
                if (def == null) return false;

                ConfigurationSection perms = sec.getConfigurationSection("permissions");
                return perms != null && perms.isBoolean("permissions.build") && !perms.getBoolean("permissions.build");
            }
        });

        graph.addPlotter(new BooleanPlotter("Uses permissions.yml") {
            @Override
            protected boolean value() {
                File file = new File("permissions.yml");
                return file.isFile() && file.length() > 5;

            }
        });

        graph.addPlotter(new BooleanPlotter("Wizards") {
            @Override
            protected boolean value() {
                ConfigurationSection sec = getSection("users");
                return sec != null && sec.isConfigurationSection("ConspiracyWizard");
            }
        });

        graph.addPlotter(new BooleanPlotter("Debug mode") {
            @Override
            protected boolean value() {
                return plugin.getConfig().getBoolean("debug", false);
            }
        });

        graph.addPlotter(new BooleanPlotter("API Used") {
            @Override
            protected boolean value() {
                return apiUsed;
            }
        });
    }

    private boolean antiBuildEnabled() {
        ConfigurationSection section = getSection("groups");
        if (section == null) return false;

        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                ConfigurationSection group = section.getConfigurationSection(key);

                if (group.isConfigurationSection("permissions")) {
                    if (!group.getConfigurationSection("permissions").getBoolean("permissions.build", true)) {
                        return true;
                    }
                }
                if (group.isConfigurationSection("worlds")) {
                    ConfigurationSection worlds = group.getConfigurationSection("worlds");
                    for (String world : worlds.getKeys(false)) {
                        if (worlds.isConfigurationSection(world)) {
                            if (!worlds.getConfigurationSection(world).getBoolean("permissions.build", true)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private void setupUsage() {
        Metrics.Graph graph = metrics.createGraph("Usage");
        usage = graph;

        graph.addPlotter(new Metrics.Plotter("Users") {
            @Override
            public int getValue() {
                ConfigurationSection sec = getSection("users");
                return sec == null ? 0 : sec.getKeys(false).size();
            }
        });

        graph.addPlotter(new Metrics.Plotter("Groups") {
            @Override
            public int getValue() {
                ConfigurationSection sec = getSection("groups");
                return sec == null ? 0 : sec.getKeys(false).size();
            }
        });

        graph.addPlotter(new Metrics.Plotter("Permissions") {
            @Override
            public int getValue() {
                return count(getSection("groups")) + count(getSection("users"));
            }

            private int count(ConfigurationSection section) {
                if (section == null) return 0;

                int total = 0;
                for (String key : section.getKeys(false)) {
                    if (section.isConfigurationSection(key)) {
                        ConfigurationSection individual = section.getConfigurationSection(key);

                        if (individual.isConfigurationSection("permissions")) {
                            total += individual.getConfigurationSection("permissions").getKeys(false).size();
                        }

                        if (individual.isConfigurationSection("worlds")) {
                            ConfigurationSection worlds = individual.getConfigurationSection("worlds");
                            for (String world : worlds.getKeys(false)) {
                                if (worlds.isConfigurationSection(world)) {
                                    total += worlds.getConfigurationSection(world).getKeys(false).size();
                                }
                            }
                        }
                    }
                }
                return total;
            }
        });

        graph.addPlotter(new Metrics.Plotter("Permission Roots") {
            @Override
            public int getValue() {
                Set<String> roots = new HashSet<String>();
                fill(roots, getSection("groups"));
                fill(roots, getSection("users"));
                return roots.size();
            }

            private void fill(Set<String> results, ConfigurationSection section) {
                if (section == null) return;

                for (String key : section.getKeys(false)) {
                    if (section.isConfigurationSection(key)) {
                        ConfigurationSection individual = section.getConfigurationSection(key);

                        if (individual.isConfigurationSection("permissions")) {
                            for (String node : individual.getConfigurationSection("permissions").getKeys(false)) {
                                int i = node.indexOf('.');
                                results.add(i >= 0 ? node.substring(0, i) : node);
                            }
                        }

                        if (individual.isConfigurationSection("worlds")) {
                            ConfigurationSection worlds = individual.getConfigurationSection("worlds");
                            for (String world : worlds.getKeys(false)) {
                                if (worlds.isConfigurationSection(world)) {
                                    for (String node : worlds.getConfigurationSection(world).getKeys(false)) {
                                        int i = node.indexOf('.');
                                        results.add(i >= 0 ? node.substring(0, i) : node);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        graph.addPlotter(new Metrics.Plotter("Worlds") {
            @Override
            public int getValue() {
                Set<String> worlds = new HashSet<String>();
                fill(worlds, getSection("groups"));
                fill(worlds, getSection("users"));
                return worlds.size();
            }

            private void fill(Set<String> results, ConfigurationSection section) {
                if (section == null) return;

                for (String key : section.getKeys(false)) {
                    if (section.isConfigurationSection(key)) {
                        ConfigurationSection individual = section.getConfigurationSection(key);
                        if (individual.isConfigurationSection("worlds")) {
                            ConfigurationSection worlds = individual.getConfigurationSection("worlds");
                            results.addAll(worlds.getKeys(false));
                        }
                    }
                }
            }
        });
    }

    private abstract static class BooleanPlotter extends Metrics.Plotter {
        protected BooleanPlotter(String name) {
            super(name);
        }

        @Override
        public final int getValue() {
            try {
                return value() ? 1 : 0;
            } catch (Throwable t) {
                return 0;
            }
        }

        protected abstract boolean value();
    }
}