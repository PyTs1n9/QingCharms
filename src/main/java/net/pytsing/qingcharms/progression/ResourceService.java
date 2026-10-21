package net.pytsing.qingcharms.progression;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Checks and consumes progression resources. It never calculates combat values.
 */
public final class ResourceService {

    public boolean canConsume(Player player, ProgressionRecord record,
                              ResourceCost cost, StringBuilder error) {
        if (cost == null || cost.isEmpty()) {
            return true;
        }
        for (Map.Entry<ResourceType, Double> entry : cost.getAmounts().entrySet()) {
            double amount = entry.getValue().doubleValue();
            if (entry.getKey() == ResourceType.WEAR_SECONDS
                    && record.getWearSeconds() + 0.0001D < amount) {
                error.append("佩戴时间");
                return false;
            }
            if (entry.getKey() == ResourceType.KILLS
                    && record.getKills() + 0.0001D < amount) {
                error.append("击杀数量");
                return false;
            }
            if (entry.getKey() == ResourceType.EXPERIENCE
                    && player.getTotalExperience() + 0.0001D < amount) {
                error.append("原版经验");
                return false;
            }
            if (entry.getKey() == ResourceType.VAULT && !hasVault(player, amount, error)) {
                return false;
            }
            if (entry.getKey() == ResourceType.PLAYER_POINTS
                    && !hasPoints(player, amount, error)) {
                return false;
            }
        }
        return true;
    }

    public boolean consume(Player player, ProgressionRecord record,
                           ResourceCost cost, StringBuilder error) {
        if (!canConsume(player, record, cost, error)) {
            return false;
        }
        if (cost == null || cost.isEmpty()) {
            return true;
        }

        long oldWear = record.getWearSeconds();
        long oldKills = record.getKills();
        double consumedExperience = 0.0D;
        double consumedVault = 0.0D;
        double consumedPoints = 0.0D;
        try {
            for (Map.Entry<ResourceType, Double> entry : cost.getAmounts().entrySet()) {
                double amount = entry.getValue().doubleValue();
                if (entry.getKey() == ResourceType.WEAR_SECONDS) {
                    record.setWearSeconds(record.getWearSeconds() - Math.round(amount));
                } else if (entry.getKey() == ResourceType.KILLS) {
                    record.setKills(record.getKills() - Math.round(amount));
                } else if (entry.getKey() == ResourceType.EXPERIENCE) {
                    int value = (int) Math.min(Integer.MAX_VALUE, Math.round(amount));
                    player.giveExp(-value);
                    consumedExperience += value;
                } else if (entry.getKey() == ResourceType.VAULT) {
                    if (!withdrawVault(player, amount)) {
                        throw new IllegalStateException("Vault withdrawal failed");
                    }
                    consumedVault += amount;
                } else if (entry.getKey() == ResourceType.PLAYER_POINTS) {
                    int value = (int) Math.min(Integer.MAX_VALUE, Math.round(amount));
                    if (!takePoints(player, value)) {
                        throw new IllegalStateException("PlayerPoints withdrawal failed");
                    }
                    consumedPoints += value;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            record.setWearSeconds(oldWear);
            record.setKills(oldKills);
            if (consumedExperience > 0.0D) {
                player.giveExp((int) consumedExperience);
            }
            if (consumedVault > 0.0D) {
                depositVault(player, consumedVault);
            }
            if (consumedPoints > 0.0D) {
                givePoints(player, (int) consumedPoints);
            }
            error.append("资源扣除失败");
            return false;
        }
    }

    private boolean hasVault(Player player, double amount, StringBuilder error) {
        Object economy = vaultEconomy();
        if (economy == null) {
            error.append("Vault");
            return false;
        }
        try {
            Method has = findMethod(economy, "has", player.getClass());
            if (has != null) {
                Object result = invoke(economy, has, player, Double.valueOf(amount));
                return Boolean.TRUE.equals(result);
            }
            Method balance = findMethod(economy, "getBalance", player.getClass());
            if (balance == null) {
                error.append("Vault");
                return false;
            }
            Object result = invoke(economy, balance, player);
            return result instanceof Number
                    && ((Number) result).doubleValue() >= amount;
        } catch (RuntimeException exception) {
            error.append("Vault");
            return false;
        }
    }

    private boolean withdrawVault(Player player, double amount) {
        Object economy = vaultEconomy();
        if (economy == null) {
            return false;
        }
        try {
            Method method = findMethod(economy, "withdrawPlayer", player.getClass());
            if (method == null) {
                return false;
            }
            return responseSuccess(invoke(economy, method, player, Double.valueOf(amount)));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void depositVault(Player player, double amount) {
        Object economy = vaultEconomy();
        if (economy == null) {
            return;
        }
        try {
            Method method = findMethod(economy, "depositPlayer", player.getClass());
            if (method != null) {
                invoke(economy, method, player, Double.valueOf(amount));
            }
        } catch (RuntimeException ignored) {
            // Compensation is best-effort only after an unexpected provider failure.
        }
    }

    private Object vaultEconomy() {
        try {
            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration(economyClass);
            return registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private boolean hasPoints(Player player, double amount, StringBuilder error) {
        Object api = playerPointsApi();
        if (api == null) {
            error.append("PlayerPoints");
            return false;
        }
        try {
            Method method = findMethod(api, "getPoints", player.getUniqueId().getClass());
            Object result = invoke(api, method, player.getUniqueId());
            return result instanceof Number
                    && ((Number) result).doubleValue() >= amount;
        } catch (RuntimeException exception) {
            error.append("PlayerPoints");
            return false;
        }
    }

    private boolean takePoints(Player player, int amount) {
        Object api = playerPointsApi();
        try {
            Method method = findMethod(api, "takePoints", player.getUniqueId().getClass());
            Object result = invoke(api, method, player.getUniqueId(), Integer.valueOf(amount));
            return !(result instanceof Boolean) || Boolean.TRUE.equals(result);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void givePoints(Player player, int amount) {
        Object api = playerPointsApi();
        try {
            Method method = findMethod(api, "givePoints", player.getUniqueId().getClass());
            if (method != null) {
                invoke(api, method, player.getUniqueId(), Integer.valueOf(amount));
            }
        } catch (RuntimeException ignored) {
            // Compensation is best-effort only after an unexpected provider failure.
        }
    }

    private Object playerPointsApi() {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Method getter = plugin.getClass().getMethod("getAPI");
            return getter.invoke(plugin);
        } catch (ReflectiveOperationException ignored) {
            return plugin;
        }
    }

    private Method findMethod(Object target, String name, Class<?> argument) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isAssignableFrom(argument)) {
                return method;
            }
            if (parameters.length == 2
                    && parameters[0].isAssignableFrom(argument)
                    && (parameters[1] == Integer.TYPE || parameters[1] == Integer.class
                    || parameters[1] == Double.TYPE || parameters[1] == Double.class)) {
                return method;
            }
        }
        return null;
    }

    private Object invoke(Object target, Method method, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean responseSuccess(Object response) {
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(method.invoke(response));
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }
}

