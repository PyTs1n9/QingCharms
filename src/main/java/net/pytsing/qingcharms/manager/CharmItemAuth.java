package net.pytsing.qingcharms.manager;

import net.pytsing.qingcharms.api.ItemUniqueId;
import net.pytsing.qingcharms.config.ConfigManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Issues and verifies server-signed charm markers. Visual matching alone is
 * never sufficient when authentication is enabled.
 */
public final class CharmItemAuth {

    private static final String TOKEN_PREFIX = "@qc-auth|1|";
    // qcItems files may be named in Chinese, so the id inside the token accepts
    // any printable character except the token separator and colour markers.
    private static final Pattern CHARM_ID = Pattern.compile("[^\\s\\u00a7|]{1,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaPlugin plugin;
    private final byte[] secret;

    public CharmItemAuth(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        String configured = plugin.getConfig()
                .getString("settings.charm-auth-secret", "").trim();
        if (configured.isEmpty()) {
            byte[] generated = new byte[32];
            RANDOM.nextBytes(generated);
            configured = Base64.getUrlEncoder().withoutPadding().encodeToString(generated);
            plugin.getConfig().set("settings.charm-auth-secret", configured);
            configManager.save();
            plugin.getLogger().info("Generated a persistent charm authentication key.");
        }
        this.secret = decodeSecret(configured);
    }

    public ItemStack issue(ItemStack item, String charmId) {
        if (item == null || charmId == null || !CHARM_ID.matcher(charmId).matches()) {
            return item;
        }
        item.setAmount(1);
        String uniqueId = UUID.randomUUID().toString();
        ItemStack issued = ItemUniqueId.withUniqueId(item, uniqueId);
        return ItemUniqueId.withIdentityToken(
                issued, createToken(charmId, uniqueId, secret));
    }

    public boolean isAuthentic(ItemStack item) {
        String token = ItemUniqueId.identityTokenFrom(item);
        if (token.isEmpty()) {
            return !isAuthenticationRequired();
        }
        String uniqueId = ItemUniqueId.explicitFrom(item);
        return !uniqueId.isEmpty() && verifyToken(token, secret, uniqueId);
    }

    public String getCharmId(ItemStack item) {
        String token = ItemUniqueId.identityTokenFrom(item);
        String uniqueId = ItemUniqueId.explicitFrom(item);
        if (uniqueId.isEmpty() || !verifyToken(token, secret, uniqueId)) {
            return "";
        }
        int split = token.lastIndexOf('|');
        return split < TOKEN_PREFIX.length()
                ? "" : token.substring(TOKEN_PREFIX.length(), split);
    }

    static String createToken(String charmId, String uniqueId, byte[] secret) {
        return TOKEN_PREFIX + charmId + '|'
                + sign(charmId + '|' + uniqueId, secret);
    }

    static boolean verifyToken(String token, byte[] secret, String uniqueId) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)
                || uniqueId == null || uniqueId.isEmpty()) {
            return false;
        }
        int split = token.lastIndexOf('|');
        if (split <= TOKEN_PREFIX.length() || split == token.length() - 1) {
            return false;
        }
        String charmId = token.substring(TOKEN_PREFIX.length(), split);
        String signature = token.substring(split + 1);
        if (!CHARM_ID.matcher(charmId).matches()) {
            return false;
        }
        byte[] expected = sign(charmId + '|' + uniqueId, secret)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private boolean isAuthenticationRequired() {
        return plugin.getConfig()
                .getBoolean("settings.require-charm-authentication", true);
    }

    private static String sign(String value, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] signed = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        } catch (java.security.InvalidKeyException exception) {
            throw new IllegalStateException("Invalid charm authentication key", exception);
        }
    }

    private static byte[] decodeSecret(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
