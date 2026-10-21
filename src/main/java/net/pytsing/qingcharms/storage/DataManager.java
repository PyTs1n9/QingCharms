package net.pytsing.qingcharms.storage;

import net.pytsing.qingcharms.model.PlayerData;

import java.util.UUID;

public interface DataManager {

    void load();

    PlayerData loadPlayer(UUID playerId);

    void savePlayer(PlayerData playerData);

    void savePlayer(UUID playerId);

    void unload(UUID playerId);

    void saveAll();

    int saveAllNow();

    SaveStatus flushPending();

    void flush();

    BackupStatus backupNow();

    int getCacheSize();

    int getDirtyCount();

    boolean isReadOnly();

    long getStoredYamlBytes();

    int getBackupCount();
    long getDiskFreeBytes();

    SaveStatus getLastSaveStatus();

    BackupStatus getLastBackupStatus();

    void shutdown();

    void unloadAll();
}

