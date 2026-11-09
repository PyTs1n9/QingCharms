package net.pytsing.qingcharms.support;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Minimal Bukkit interfaces for item and inventory tests; no server is booted. */
public final class BukkitFixture {

    private BukkitFixture() {
    }

    public static void install() {
        ConfigurationSerialization.registerClass(TestMeta.class);
        if (Bukkit.getServer() != null) {
            return;
        }
        final ItemFactory factory = proxy(ItemFactory.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getItemMeta".equals(name)) {
                    return args[0] == Material.AIR ? null : new TestMeta();
                }
                if ("equals".equals(name) && args != null && args.length == 2) {
                    TestMeta first = emptyMeta(args[0]);
                    TestMeta second = emptyMeta(args[1]);
                    return first.serialize().equals(second.serialize());
                }
                if ("isApplicable".equals(name)) {
                    return args[0] == null || args[0] instanceof TestMeta;
                }
                if ("asMetaFor".equals(name)) {
                    return args[0] == null ? null : ((ItemMeta) args[0]).clone();
                }
                if ("getDefaultLeatherColor".equals(name)) {
                    return Color.fromRGB(0xA06540);
                }
                return objectMethod(proxy, method, args);
            }
        });
        Bukkit.setServer(proxy(Server.class, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getItemFactory".equals(name)) {
                    return factory;
                }
                if ("getLogger".equals(name)) {
                    return Logger.getLogger("QingCharms-test");
                }
                if ("isPrimaryThread".equals(name)) {
                    return Boolean.TRUE;
                }
                if ("getName".equals(name) || "getVersion".equals(name)
                        || "getBukkitVersion".equals(name)) {
                    return "QingCharms-test";
                }
                return objectMethod(proxy, method, args);
            }
        }));
    }

    private static TestMeta emptyMeta(Object value) {
        return value == null ? new TestMeta() : (TestMeta) value;
    }

    public static ItemStack item(Material material, int amount, String name,
                                 String... lore) {
        ItemStack result = new ItemStack(material, amount);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore.length == 0 ? null : Arrays.asList(lore));
        result.setItemMeta(meta);
        return result;
    }

    public static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? null : source[i].clone();
        }
        return result;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] { type }, handler));
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
            return proxy == args[0];
        }
        if ("toString".equals(method.getName())) {
            return "QingCharms-test-" + proxy.getClass().getInterfaces()[0].getSimpleName();
        }
        throw new UnsupportedOperationException("Unexpected Bukkit call: " + method);
    }

    /** Uses Bukkit's documented unit-test constructor instead of a plugin classloader. */
    public static final class TestPlugin extends JavaPlugin {
        private final YamlConfiguration configuration = new YamlConfiguration();

        @SuppressWarnings("deprecation")
        public TestPlugin(File folder) {
            super((PluginLoader) null, Bukkit.getServer(),
                    new PluginDescriptionFile("QingCharms-test", "0.0.0", "test.Plugin"),
                    folder, new File(folder, "test.jar"));
            configuration.set("settings.charm-auth-secret", "dGVzdC1jaGFybS1zZWNyZXQ");
        }

        @Override public YamlConfiguration getConfig() { return configuration; }
        @Override public void saveConfig() { }
    }

    public static final class Inventory {
        private ItemStack[] contents;
        private final PlayerInventory inventory;
        private final Player player;
        public int heldSlot;
        public int setContentsCalls;
        public int failedWrites;
        public boolean alwaysFailWrites;
        public int updates;

        public Inventory(int size) {
            contents = new ItemStack[size];
            inventory = proxy(PlayerInventory.class, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("getContents".equals(name)) {
                        return copy(contents);
                    }
                    if ("setContents".equals(name)) {
                        setContentsCalls++;
                        ItemStack[] replacement = (ItemStack[]) args[0];
                        if (alwaysFailWrites || failedWrites > 0) {
                            failedWrites = Math.max(0, failedWrites - 1);
                            contents[0] = replacement[0] == null ? null : replacement[0].clone();
                            throw new IllegalStateException("injected partial inventory write");
                        }
                        contents = copy(replacement);
                        return null;
                    }
                    if ("getItemInHand".equals(name)) {
                        return get(heldSlot);
                    }
                    if ("setItemInHand".equals(name)) {
                        put(heldSlot, (ItemStack) args[0]);
                        return null;
                    }
                    if ("getItem".equals(name)) {
                        return get((Integer) args[0]);
                    }
                    if ("setItem".equals(name)) {
                        put((Integer) args[0], (ItemStack) args[1]);
                        return null;
                    }
                    if ("getHeldItemSlot".equals(name)) {
                        return heldSlot;
                    }
                    if ("getMaxStackSize".equals(name)) {
                        return 64;
                    }
                    if ("getSize".equals(name)) {
                        return contents.length;
                    }
                    if ("getArmorContents".equals(name)) {
                        return Arrays.copyOfRange(copy(contents), 36, contents.length);
                    }
                    return objectMethod(proxy, method, args);
                }
            });
            player = proxy(Player.class, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("getInventory".equals(name)) {
                        return inventory;
                    }
                    if ("getItemInHand".equals(name)) {
                        return get(heldSlot);
                    }
                    if ("setItemInHand".equals(name)) {
                        put(heldSlot, (ItemStack) args[0]);
                        return null;
                    }
                    if ("updateInventory".equals(name)) {
                        updates++;
                        return null;
                    }
                    return objectMethod(proxy, method, args);
                }
            });
        }

        public Player player() {
            return player;
        }

        public PlayerInventory inventory() {
            return inventory;
        }

        public ItemStack get(int index) {
            return contents[index] == null ? null : contents[index].clone();
        }

        public void put(int index, ItemStack item) {
            contents[index] = item == null ? null : item.clone();
        }

        public ItemStack[] snapshot() {
            return copy(contents);
        }

        public void fillStorage() {
            for (int i = 0; i < Math.min(36, contents.length); i++) {
                put(i, new ItemStack(Material.COBBLESTONE, 64));
            }
        }
    }

    @SerializableAs("QingCharmsTestMeta")
    public static final class TestMeta implements ItemMeta {
        private String displayName;
        private List<String> lore;
        private final Map<Enchantment, Integer> enchants =
                new LinkedHashMap<Enchantment, Integer>();
        private final Set<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);

        public TestMeta() {
        }

        public TestMeta(Map<String, Object> values) {
            displayName = (String) values.get("display-name");
            Object lines = values.get("lore");
            if (lines instanceof List) {
                lore = new ArrayList<String>();
                for (Object line : (List<?>) lines) {
                    lore.add(String.valueOf(line));
                }
            }
            Object level = values.get("durability-enchant");
            if (level instanceof Number) {
                enchants.put(Enchantment.DURABILITY, ((Number) level).intValue());
            }
            Object serializedFlags = values.get("flags");
            if (serializedFlags instanceof List) {
                for (Object flag : (List<?>) serializedFlags) {
                    flags.add(ItemFlag.valueOf(String.valueOf(flag)));
                }
            }
        }

        @Override public boolean hasDisplayName() { return displayName != null; }
        @Override public String getDisplayName() { return displayName; }
        @Override public void setDisplayName(String value) { displayName = value; }
        @Override public boolean hasLore() { return lore != null && !lore.isEmpty(); }
        @Override public List<String> getLore() {
            return lore == null ? null : new ArrayList<String>(lore);
        }
        @Override public void setLore(List<String> value) {
            lore = value == null ? null : new ArrayList<String>(value);
        }
        @Override public boolean hasEnchants() { return !enchants.isEmpty(); }
        @Override public boolean hasEnchant(Enchantment enchantment) {
            return enchants.containsKey(enchantment);
        }
        @Override public int getEnchantLevel(Enchantment enchantment) {
            Integer value = enchants.get(enchantment);
            return value == null ? 0 : value;
        }
        @Override public Map<Enchantment, Integer> getEnchants() {
            return Collections.unmodifiableMap(new LinkedHashMap<Enchantment, Integer>(enchants));
        }
        @Override public boolean addEnchant(Enchantment enchantment, int level,
                                            boolean ignoreLevelRestriction) {
            Integer previous = enchants.put(enchantment, level);
            return previous == null || previous.intValue() != level;
        }
        @Override public boolean removeEnchant(Enchantment enchantment) {
            return enchants.remove(enchantment) != null;
        }
        @Override public boolean hasConflictingEnchant(Enchantment enchantment) {
            return false;
        }
        @Override public void addItemFlags(ItemFlag... values) {
            flags.addAll(Arrays.asList(values));
        }
        @Override public void removeItemFlags(ItemFlag... values) {
            flags.removeAll(Arrays.asList(values));
        }
        @Override public Set<ItemFlag> getItemFlags() {
            return Collections.unmodifiableSet(EnumSet.copyOf(flags));
        }
        @Override public boolean hasItemFlag(ItemFlag flag) { return flags.contains(flag); }
        @Override public ItemMeta.Spigot spigot() { return new ItemMeta.Spigot(); }
        @Override public TestMeta clone() { return new TestMeta(serialize()); }
        @Override public Map<String, Object> serialize() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            if (displayName != null) result.put("display-name", displayName);
            if (hasLore()) result.put("lore", getLore());
            if (enchants.containsKey(Enchantment.DURABILITY)) {
                result.put("durability-enchant", enchants.get(Enchantment.DURABILITY));
            }
            if (!flags.isEmpty()) {
                List<String> names = new ArrayList<String>();
                for (ItemFlag flag : flags) names.add(flag.name());
                result.put("flags", names);
            }
            return result;
        }
        @Override public boolean equals(Object value) {
            return value instanceof TestMeta && serialize().equals(((TestMeta) value).serialize());
        }
        @Override public int hashCode() { return serialize().hashCode(); }
    }
}
