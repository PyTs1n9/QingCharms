package net.pytsing.qingcharms.model;

public final class ItemIdentity {

    private final String uniqueId;
    private final String fingerprint;

    public ItemIdentity(String uniqueId, String fingerprint) {
        this.uniqueId = uniqueId == null ? "" : uniqueId.trim();
        this.fingerprint = fingerprint == null ? "" : fingerprint.trim();
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

}
