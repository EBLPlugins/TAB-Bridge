package me.neznamy.tab.bridge.bukkit.nms;

import lombok.Getter;
import me.neznamy.tab.bridge.shared.util.ReflectionUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class BukkitReflection {

    /** Server's NMS/CraftBukkit package */
    @Getter
    private static final String serverPackage = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

    /** Server's minor version */
    @Getter
    private static final int minorVersion = Integer.parseInt(serverPackage.split("_")[1]);

    /** Flag determining whether the server version is at least 1.19.3 or not */
    @Getter
    private static final boolean is1_19_3Plus = ReflectionUtils.classExists("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");

    /** Flag determining whether the server version is at least 1.19.4 or not */
    @Getter
    private static final boolean is1_19_4Plus = is1_19_3Plus && !serverPackage.equals("v1_19_R2");

    /** Flag determining whether the server version is at least 1.20.2 or not */
    @Getter
    private static final boolean is1_20_2Plus = ReflectionUtils.classExists("net.minecraft.world.scores.DisplaySlot");

    /** Flag determining whether the server version is at least 1.20.3 or not */
    @Getter
    private static final boolean is1_20_3Plus = ReflectionUtils.classExists("net.minecraft.network.protocol.game.ClientboundResetScorePacket");

    /**
     * Returns class with given potential names in same order. For 1.17+ it takes packaged class names
     * without "net.minecraft." prefix, for <1.17 it takes class name only.
     *
     * @param   names
     *          possible class names
     * @return  class for specified names
     * @throws  ClassNotFoundException
     *          if class does not exist
     */
    public static Class<?> getClass(@NotNull String... names) throws ClassNotFoundException {
        ClassLoader loader = BukkitReflection.class.getClassLoader();
        for (String name : names) {
            try {
                if (minorVersion >= 17) {
                    return Class.forName("net.minecraft." + name);
                } else {
                    return loader.loadClass("net.minecraft.server." + serverPackage + "." + name);
                }
            } catch (ClassNotFoundException | NullPointerException ignored) {
                // not the first class name in array
            }
        }
        throw new ClassNotFoundException("No class found with possible names " + Arrays.toString(names));
    }
}