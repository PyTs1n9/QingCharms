package net.pytsing.qingcharms.util;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

/**
 * Central Bukkit event dispatch with an explicit main-thread contract.
 */
public final class EventUtils {

    private EventUtils() {
    }

    public static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "QingCharms events and mutations must run on the Bukkit main thread");
        }
    }

    public static void call(Event event) {
        requireMainThread();
        Bukkit.getPluginManager().callEvent(event);
    }

    public static <T extends Event & Cancellable> boolean callCancellable(T event) {
        call(event);
        return !event.isCancelled();
    }
}