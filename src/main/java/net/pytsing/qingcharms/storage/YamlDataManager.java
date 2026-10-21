package net.pytsing.qingcharms.storage;

import net.pytsing.qingcharms.model.ItemIdentity;
import net.pytsing.qingcharms.model.PlayerData;
import net.pytsing.qingcharms.progression.ProgressionRecord;
import net.pytsing.qingcharms.util.YamlUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class YamlDataManager implements DataManager {

    private static final int MIN_FLUSH_SECONDS = 30;
    private static final int MAX_FLUSH_SECONDS = 60;
    private static final long RETRY_SECONDS = 5L;
    private static final long FLUSH_WAIT_SECONDS = 30L;

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<UUID, PlayerData>();
    private final ConcurrentMap<UUID, PlayerSnapshot> writeQueue =
            new ConcurrentHashMap<UUID, PlayerSnapshot>();
    private final ScheduledExecutorService ioExecutor;
    private File legacyDataFile;
    private File playerDataFolder;
    private File backupFolder;
    private BukkitTask flushTask;
    private ScheduledFuture<?> backupTask;
    private volatile boolean closing;
    private volatile boolean closed;
    private volatile boolean readOnly;
    private volatile SaveStatus lastSaveStatus = SaveStatus.never();
    private volatile BackupStatus lastBackupStatus = BackupStatus.never();
    private volatile long lastSaveFailureLog;

    public YamlDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ioExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "QingCharms-PlayerData-IO-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void load() {
        legacyDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        backupFolder = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("settings.player-backup-folder", "DataBacked"));
        cache.clear();
        writeQueue.clear();

        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            readOnly = true;
            plugin.getLogger().log(Level.SEVERE, "Could not create player data directory: "
                    + playerDataFolder.getAbsolutePath());
        } else {
            readOnly = !playerDataFolder.isDirectory();
        }
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create backup directory: "
                    + backupFolder.getAbsolutePath());
        }

        YamlUtils.LoadResult legacyResult = YamlUtils.loadChecked(legacyDataFile, plugin.getLogger());
        if (legacyResult.isFailed()) {
            plugin.getLogger().warning("playerdata.yml could not be parsed; per-player files remain writable.");
        } else {
            loadConfiguration(legacyResult.getConfiguration(), legacyDataFile);
        }

        File[] playerFiles = playerDataFolder.listFiles();
        if (playerFiles != null) {
            Arrays.sort(playerFiles);
            for (File file : playerFiles) {
                if (!file.isFile() || !file.getName().endsWith(".yml")) {
                    continue;
                }
                YamlUtils.LoadResult result = YamlUtils.loadChecked(file, plugin.getLogger());
                if (result.isFailed()) {
                    plugin.getLogger().warning("Skipping unreadable player data file: "
                            + file.getAbsolutePath());
                } else {
                    loadConfiguration(result.getConfiguration(), file);
                }
            }
        }

        long flushSeconds = clamp(plugin.getConfig().getLong(
                "settings.player-save-interval-seconds", 30L),
                MIN_FLUSH_SECONDS, MAX_FLUSH_SECONDS);
        long flushTicks = flushSeconds * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                saveAll();
            }
        }, flushTicks, flushTicks);

        long backupMinutes = clamp(plugin.getConfig().getLong(
                "settings.player-backup-interval-minutes", 60L), 1L, 1440L);
        backupTask = ioExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                backupNow();
            }
        }, backupMinutes, backupMinutes, TimeUnit.MINUTES);
    }

    private void loadConfiguration(YamlConfiguration configuration, File source) {
        ConfigurationSection root = configuration.getConfigurationSection("players");
        if (root == null) {
            if (configuration.contains("players")) {
                plugin.getLogger().warning("Invalid players section in " + source.getName());
            }
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                ConfigurationSection playerSection = root.getConfigurationSection(key);
                if (playerSection == null) {
                    plugin.getLogger().warning("Invalid player entry in " + source.getName() + ": " + key);
                    continue;
                }
                PlayerData data = new PlayerData(playerId);
                ConfigurationSection charms = playerSection.getConfigurationSection("charms");
                if (playerSection.contains("charms") && charms == null) {
                    plugin.getLogger().warning("Invalid charms section in " + source.getName() + ": " + key);
                }
                if (charms != null) {
                    for (String charmId : charms.getKeys(false)) {
                        if (Boolean.TRUE.equals(charms.get(charmId))) {
                            data.addCharm(charmId);
                        }
                    }
                }
                ConfigurationSection itemIds = playerSection.getConfigurationSection("item-ids");
                if (playerSection.contains("item-ids") && itemIds == null) {
                    plugin.getLogger().warning("Invalid item-ids section in " + source.getName()
                            + ": " + key);
                }
                if (itemIds != null) {
                    Map<String, ItemIdentity> identities =
                            new LinkedHashMap<String, ItemIdentity>();
                    for (String itemKey : itemIds.getKeys(false)) {
                        ConfigurationSection identity = itemIds.getConfigurationSection(itemKey);
                        if (identity != null) {
                            String uniqueId = identity.getString("unique-id", "");
                            String fingerprint = identity.getString("fingerprint", "");
                            if (!uniqueId.isEmpty() && !fingerprint.isEmpty()) {
                                identities.put(itemKey,
                                        new ItemIdentity(uniqueId, fingerprint));
                            }
                        }
                    }
                    data.restoreItemIdentities(identities);
                }

                ConfigurationSection equipped = playerSection.getConfigurationSection("equipped");
                if (playerSection.contains("equipped") && equipped == null) {
                    plugin.getLogger().warning("Invalid equipped section in " + source.getName() + ": " + key);
                }
                if (equipped != null) {
                    for (String slotId : equipped.getKeys(false)) {
                        ItemStack item = equipped.getItemStack(slotId);
                        if (item == null) {
                            plugin.getLogger().warning("Invalid equipped item in " + source.getName()
                                    + ": " + key + "/" + slotId);
                        } else {
                            data.setEquipped(slotId, item);
                        }
                    }
                }
                ConfigurationSection progression = playerSection.getConfigurationSection("progression");
                if (playerSection.contains("progression") && progression == null) {
                    plugin.getLogger().warning("Invalid progression section in " + source.getName());
                }
                if (progression != null) {
                    for (String uniqueId : progression.getKeys(false)) {
                        data.putProgression(ProgressionRecord.load(uniqueId,
                                progression.getConfigurationSection(uniqueId)));
                    }
                }

                data.resetDirty();
                cache.put(playerId, data);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid player UUID in " + source.getName() + ": " + key);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load player data from "
                        + source.getName() + ": " + key, exception);
            }
        }
    }

    @Override
    public PlayerData loadPlayer(UUID playerId) {
        PlayerData data = cache.get(playerId);
        if (data == null) {
            data = new PlayerData(playerId);
            cache.put(playerId, data);
        }
        return data;
    }

    @Override
    public void savePlayer(PlayerData playerData) {
        if (playerData == null || closed) {
            return;
        }
        cache.put(playerData.getOwner(), playerData);
        if (playerData.isDirty()) {
            enqueueSnapshot(playerData);
        }
    }

    @Override
    public void savePlayer(UUID playerId) {
        if (playerId == null || closed) {
            return;
        }
        PlayerData data = cache.get(playerId);
        if (data != null && data.isDirty()) {
            enqueueSnapshot(data);
        }
    }

    @Override
    public void unload(UUID playerId) {
        PlayerData data = cache.remove(playerId);
        if (data != null && data.isDirty() && !closed) {
            enqueueSnapshot(data);
        }
    }

    @Override
    public void saveAll() {
        if (closed) {
            return;
        }
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                enqueueSnapshot(data);
            }
        }
    }

    @Override
    public int saveAllNow() {
        if (closed) {
            return 0;
        }
        int count = 0;
        for (PlayerData data : cache.values()) {
            enqueueSnapshot(data);
            count++;
        }
        if (count == 0) {
            lastSaveStatus = new SaveStatus(System.currentTimeMillis(), 0L, true, 0,
                    "没有缓存玩家数据");
        }
        return count;
    }

    @Override
    public SaveStatus flushPending() {
        if (closed) {
            return lastSaveStatus;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    drainWriteQueue();
                    long deadline = System.nanoTime()
                            + TimeUnit.SECONDS.toNanos(FLUSH_WAIT_SECONDS);
                    while (!writeQueue.isEmpty() && System.nanoTime() < deadline) {
                        try {
                            Thread.sleep(250L);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        drainWriteQueue();
                    }
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            if (!latch.await(FLUSH_WAIT_SECONDS + 5L, TimeUnit.SECONDS)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Timed out while forcing player data to disk.");
                lastSaveStatus = new SaveStatus(System.currentTimeMillis(),
                        TimeUnit.SECONDS.toNanos(FLUSH_WAIT_SECONDS), false,
                        writeQueue.size(), "保存超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Interrupted while forcing player data to disk.");
            lastSaveStatus = new SaveStatus(System.currentTimeMillis(), 0L, false,
                    writeQueue.size(), "保存被中断");
        }
        return lastSaveStatus;
    }

    @Override
    public void flush() {
        saveAll();
        flushPending();
    }

    @Override
    public void shutdown() {
        if (closed) {
            return;
        }
        closing = true;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (backupTask != null) {
            backupTask.cancel(false);
            backupTask = null;
        }
        saveAllNow();
        flushPending();
        closed = true;
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    @Override
    public void unloadAll() {
        for (UUID playerId : new ArrayList<UUID>(cache.keySet())) {
            unload(playerId);
        }
    }

    private void enqueueSnapshot(final PlayerData data) {
        if (closed) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    enqueueSnapshot(data);
                }
            });
            return;
        }
        PlayerSnapshot snapshot = PlayerSnapshot.from(data);
        writeQueue.put(data.getOwner(), snapshot);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                drainWriteQueue();
            }
        });
    }

    private void drainWriteQueue() {
        long started = System.nanoTime();
        Iterator<Map.Entry<UUID, PlayerSnapshot>> iterator =
                writeQueue.entrySet().iterator();
        boolean failed = false;
        int attempted = 0;
        int saved = 0;
        String firstError = "";
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerSnapshot> entry = iterator.next();
            PlayerSnapshot snapshot = entry.getValue();
            attempted++;
            try {
                writeSnapshot(snapshot);
                if (writeQueue.remove(entry.getKey(), snapshot)) {
                    snapshot.data.markClean(snapshot.revision);
                }
                saved++;
            } catch (Exception exception) {
                failed = true;
                if (firstError.isEmpty()) {
                    firstError = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                }
                long now = System.currentTimeMillis();
                if (now - lastSaveFailureLog >= 60000L) {
                    lastSaveFailureLog = now;
                    plugin.getLogger().log(Level.SEVERE,
                            "[QingCharms][SAVE FAILED] 玩家数据保存失败，已进入 5 秒重试队列。",
                            exception);
                } else {
                    plugin.getLogger().warning("[QingCharms] 玩家 " + entry.getKey()
                            + " 数据仍在重试：" + exception.getMessage());
                }
            }
        }
        if (attempted > 0) {
            long duration = System.nanoTime() - started;
            lastSaveStatus = new SaveStatus(System.currentTimeMillis(), duration,
                    !failed, attempted,
                    failed ? "失败 " + (attempted - saved) + " 个：" + firstError
                            : "成功保存 " + saved + " 个玩家");
        }
        if (failed && !closing && !closed) {
            ioExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    drainWriteQueue();
                }
            }, RETRY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void writeSnapshot(PlayerSnapshot snapshot) throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection root = configuration.createSection("players");
        ConfigurationSection playerSection = root.createSection(snapshot.owner.toString());
        ConfigurationSection charms = playerSection.createSection("charms");
        for (String charmId : snapshot.charmIds) {
            charms.set(charmId, Boolean.TRUE);
        }
        ConfigurationSection equipped = playerSection.createSection("equipped");
        for (Map.Entry<String, ItemStack> entry : snapshot.equipped.entrySet()) {
            equipped.set(entry.getKey(), entry.getValue());
        }

        ConfigurationSection itemIds = playerSection.createSection("item-ids");
        for (Map.Entry<String, ItemIdentity> entry : snapshot.itemIdentities.entrySet()) {
            ConfigurationSection identity = itemIds.createSection(entry.getKey());
            identity.set("unique-id", entry.getValue().getUniqueId());
            identity.set("fingerprint", entry.getValue().getFingerprint());
        }

        ConfigurationSection progression = playerSection.createSection("progression");
        for (Map.Entry<String, ProgressionRecord> entry : snapshot.progressions.entrySet()) {
            entry.getValue().save(progression.createSection(entry.getKey()));
        }

        File target = new File(playerDataFolder, snapshot.owner.toString() + ".yml");
        YamlUtils.save(target, configuration);
    }

    @Override
    public synchronized BackupStatus backupNow() {
        long started = System.nanoTime();
        int retention = (int) clamp(plugin.getConfig().getLong(
                "settings.player-backup-retention", 7L), 7L, 30L);
        long maxAgeHours = clamp(plugin.getConfig().getLong(
                "settings.player-backup-max-age-hours", 168L), 24L, 720L);
        if (closed) {
            lastBackupStatus = new BackupStatus(System.currentTimeMillis(), 0L,
                    false, null, "存储管理器已关闭");
            return lastBackupStatus;
        }

        try {
            if (!backupFolder.exists() && !backupFolder.mkdirs()) {
                throw new IOException("Could not create backup directory: "
                        + backupFolder.getAbsolutePath());
            }
            List<File> sources = new ArrayList<File>();
            if (legacyDataFile != null && legacyDataFile.isFile()) {
                sources.add(legacyDataFile);
            }
            File[] playerFiles = playerDataFolder.listFiles();
            if (playerFiles != null) {
                for (File file : playerFiles) {
                    if (file.isFile() && file.getName().endsWith(".yml")) {
                        sources.add(file);
                    }
                }
            }
            if (sources.isEmpty()) {
                lastBackupStatus = new BackupStatus(System.currentTimeMillis(),
                        System.nanoTime() - started, true, null, "没有可备份的玩家数据");
                return lastBackupStatus;
            }

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS")
                    .format(new java.util.Date());
            File target = new File(backupFolder, "playerdata-" + timestamp + ".zip");
            File temporary = new File(backupFolder, "." + target.getName() + ".tmp");
            try {
                try (ZipOutputStream output = new ZipOutputStream(
                        new FileOutputStream(temporary))) {
                    for (File source : sources) {
                        String name = source.equals(legacyDataFile)
                                ? source.getName()
                                : "playerdata/" + source.getName();
                        output.putNextEntry(new ZipEntry(name));
                        Files.copy(source.toPath(), output);
                        output.closeEntry();
                    }
                }
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
            trimBackups(retention, maxAgeHours);
            lastBackupStatus = new BackupStatus(System.currentTimeMillis(),
                    System.nanoTime() - started, true, target,
                    "备份成功，保留 " + getBackupCount() + " 份");
            return lastBackupStatus;
        } catch (Exception exception) {
            lastBackupStatus = new BackupStatus(System.currentTimeMillis(),
                    System.nanoTime() - started, false, null,
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
            plugin.getLogger().log(Level.SEVERE,
                    "[QingCharms][BACKUP FAILED] 玩家数据备份失败。", exception);
            return lastBackupStatus;
        }
    }

    private void trimBackups(int retention, long maxAgeHours) {
        File[] files = backupFolder.listFiles();
        if (files == null) {
            return;
        }
        List<File> backups = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".zip")) {
                backups.add(file);
            }
        }
        Collections.sort(backups, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });
        long oldestAllowed = System.currentTimeMillis()
                - TimeUnit.HOURS.toMillis(maxAgeHours);
        for (int index = 0; index < backups.size(); index++) {
            File file = backups.get(index);
            if (index >= retention || file.lastModified() < oldestAllowed) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not delete old backup: "
                            + file.getAbsolutePath());
                }
            }
        }
    }

    @Override
    public int getCacheSize() {
        return cache.size();
    }

    @Override
    public int getDirtyCount() {
        int count = 0;
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public long getStoredYamlBytes() {
        long bytes = legacyDataFile != null && legacyDataFile.isFile()
                ? legacyDataFile.length() : 0L;
        File[] files = playerDataFolder == null ? null : playerDataFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".yml")) {
                    bytes += file.length();
                }
            }
        }
        return bytes;
    }

    @Override
    public int getBackupCount() {
        File[] files = backupFolder == null ? null : backupFolder.listFiles();
        int count = 0;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".zip")) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public long getDiskFreeBytes() {
        return plugin.getDataFolder().getUsableSpace();
    }

    @Override
    public SaveStatus getLastSaveStatus() {
        return lastSaveStatus;
    }

    @Override
    public BackupStatus getLastBackupStatus() {
        return lastBackupStatus;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class PlayerSnapshot {

        private final UUID owner;
        private final long revision;
        private final PlayerData data;
        private final Set<String> charmIds;
        private final Map<String, ItemStack> equipped;
        private final Map<String, ProgressionRecord> progressions;
        private final Map<String, ItemIdentity> itemIdentities;

        private PlayerSnapshot(UUID owner, long revision, PlayerData data,
                               Set<String> charmIds, Map<String, ItemStack> equipped,
                               Map<String, ProgressionRecord> progressions,
                               Map<String, ItemIdentity> itemIdentities) {
            this.owner = owner;
            this.revision = revision;
            this.data = data;
            this.charmIds = charmIds;
            this.equipped = equipped;
            this.progressions = progressions;
            this.itemIdentities = itemIdentities;
        }

        private static PlayerSnapshot from(PlayerData data) {
            Set<String> charmIds = new LinkedHashSet<String>(data.getCharmIds());
            Map<String, ItemStack> equipped = new LinkedHashMap<String, ItemStack>();
            Map<String, ProgressionRecord> progressions = data.getProgressions();
            Map<String, ItemIdentity> itemIdentities = data.getItemIdentities();
            for (String slotId : data.getEquippedSlotIds()) {
                ItemStack item = data.getEquipped(slotId);
                if (item != null && item.getType() != Material.AIR) {
                    equipped.put(slotId, item);
                }
            }
            return new PlayerSnapshot(data.getOwner(), data.getRevision(), data,
                    Collections.unmodifiableSet(charmIds),
                    Collections.unmodifiableMap(equipped),
                    Collections.unmodifiableMap(progressions),
                    Collections.unmodifiableMap(itemIdentities));
        }
    }
}







