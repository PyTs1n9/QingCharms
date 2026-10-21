package net.pytsing.qingcharms.progression;

import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ResourceServiceTest {

    @Test
    public void consumesWearAndKillResources() {
        ProgressionRecord record = new ProgressionRecord("instance");
        record.setWearSeconds(10L);
        record.setKills(4L);
        ResourceCost cost = cost(ResourceType.WEAR_SECONDS, 5.0D,
                ResourceType.KILLS, 3.0D);

        StringBuilder error = new StringBuilder();
        assertTrue(new ResourceService().consume(player(0, false), record, cost, error));

        assertEquals(5L, record.getWearSeconds());
        assertEquals(1L, record.getKills());
        assertEquals("", error.toString());
    }

    @Test
    public void rejectsInsufficientResourcesWithoutChangingRecord() {
        ProgressionRecord record = new ProgressionRecord("instance");
        record.setWearSeconds(2L);
        record.setKills(4L);
        ResourceCost cost = cost(ResourceType.WEAR_SECONDS, 5.0D);

        StringBuilder error = new StringBuilder();
        assertFalse(new ResourceService().canConsume(player(0, false), record, cost, error));

        assertEquals(2L, record.getWearSeconds());
        assertEquals(4L, record.getKills());
        assertEquals("佩戴时间", error.toString());
    }

    @Test
    public void rollsBackPartialConsumptionWhenLaterResourceFails() {
        ProgressionRecord record = new ProgressionRecord("instance");
        record.setWearSeconds(10L);
        record.setKills(4L);
        ResourceCost cost = cost(ResourceType.WEAR_SECONDS, 5.0D,
                ResourceType.EXPERIENCE, 3.0D);

        StringBuilder error = new StringBuilder();
        assertFalse(new ResourceService().consume(player(3, true), record, cost, error));

        assertEquals(10L, record.getWearSeconds());
        assertEquals(4L, record.getKills());
        assertEquals("资源扣除失败", error.toString());
    }

    private static ResourceCost cost(Object... values) {
        Map<ResourceType, Double> amounts =
                new EnumMap<ResourceType, Double>(ResourceType.class);
        for (int index = 0; index < values.length; index += 2) {
            amounts.put((ResourceType) values[index], (Double) values[index + 1]);
        }
        return new ResourceCost(amounts);
    }

    private static Player player(int experience, final boolean failExperience) {
        final int[] current = {experience};
        return (Player) Proxy.newProxyInstance(
                ResourceServiceTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getTotalExperience".equals(method.getName())) {
                            return Integer.valueOf(current[0]);
                        }
                        if ("giveExp".equals(method.getName())) {
                            if (failExperience && ((Integer) args[0]).intValue() < 0) {
                                throw new IllegalStateException("test failure");
                            }
                            current[0] += ((Integer) args[0]).intValue();
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        if (type == double.class) {
            return Double.valueOf(0.0D);
        }
        return Integer.valueOf(0);
    }
}

