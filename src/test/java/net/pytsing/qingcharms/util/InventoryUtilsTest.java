package net.pytsing.qingcharms.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class InventoryUtilsTest {

    @Test
    public void restoresInventorySnapshotAfterPartialWrite() {
        ItemStack[] contents = new ItemStack[] {
                new ItemStack(Material.STONE, 3), null
        };
        Inventory inventory = inventory(contents, 64);

        ItemStack[] snapshot = InventoryUtils.snapshot(inventory);
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 1));
        inventory.setItem(1, new ItemStack(Material.DIRT, 2));

        InventoryUtils.restore(inventory, snapshot);

        assertEquals(Material.STONE, inventory.getItem(0).getType());
        assertEquals(3, inventory.getItem(0).getAmount());
        assertNull(inventory.getItem(1));
    }

    @Test
    public void snapshotClonesItemsAndDoesNotAliasInventoryContents() {
        ItemStack[] contents = new ItemStack[] {
                new ItemStack(Material.STONE, 3)
        };
        Inventory inventory = inventory(contents, 64);

        ItemStack[] snapshot = InventoryUtils.snapshot(inventory);
        snapshot[0].setAmount(1);

        assertNotNull(inventory.getItem(0));
        assertEquals(3, inventory.getItem(0).getAmount());
    }

    private static Inventory inventory(final ItemStack[] contents, final int maxStack) {
        return (Inventory) Proxy.newProxyInstance(
                InventoryUtilsTest.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("getContents".equals(name)) {
                            ItemStack[] copy = new ItemStack[contents.length];
                            for (int i = 0; i < contents.length; i++) {
                                copy[i] = contents[i] == null ? null : contents[i].clone();
                            }
                            return copy;
                        }
                        if ("setContents".equals(name)) {
                            ItemStack[] replacement = (ItemStack[]) args[0];
                            for (int i = 0; i < contents.length; i++) {
                                contents[i] = i < replacement.length && replacement[i] != null
                                        ? replacement[i].clone() : null;
                            }
                            return null;
                        }
                        if ("getItem".equals(name)) {
                            int slot = ((Integer) args[0]).intValue();
                            return contents[slot] == null ? null : contents[slot].clone();
                        }
                        if ("setItem".equals(name)) {
                            int slot = ((Integer) args[0]).intValue();
                            contents[slot] = args[1] == null ? null : ((ItemStack) args[1]).clone();
                            return null;
                        }
                        if ("getMaxStackSize".equals(name)) {
                            return Integer.valueOf(maxStack);
                        }
                        if ("getSize".equals(name)) {
                            return Integer.valueOf(contents.length);
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(proxy == args[0]);
                        }
                        if ("toString".equals(name)) {
                            return "test-inventory";
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
        if (type == char.class) {
            return Character.valueOf('\0');
        }
        return Integer.valueOf(0);
    }
}
