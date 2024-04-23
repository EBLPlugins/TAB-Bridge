package me.neznamy.tab.bridge.bukkit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import me.neznamy.tab.bridge.bukkit.nms.BukkitReflection;
import me.neznamy.tab.bridge.bukkit.nms.NMSStorage;
import me.neznamy.tab.bridge.shared.chat.IChatBaseComponent;
import me.neznamy.tab.bridge.shared.util.ReflectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scoreboard implementation for Bukkit, which uses packets
 * to send scoreboards to use the full potential on all versions
 * and server software without any artificial limits.
 */
@RequiredArgsConstructor
public class BukkitScoreboard implements me.neznamy.tab.bridge.shared.Scoreboard {

    @Getter
    private static boolean available;

    private static Class<?> Component;
    private static Class<?> Scoreboard;
    private static Class<?> ScoreboardObjective;
    private static Object emptyScoreboard;
    private static Class<?> NumberFormat;
    private static Constructor<?> newFixedFormat;

    // Objective packet
    public static Class<?> ObjectivePacketClass;
    private static Constructor<?> newObjectivePacket;

    private static Field Objective_RENDER_TYPE;
    private static Constructor<?> newScoreboardObjective;
    private static Method ScoreboardObjective_setDisplayName;
    private static Enum<?>[] healthDisplays;
    private static Object IScoreboardCriteria_dummy;

    private static ScorePacketData scorePacketData;
    public static TeamPacketData teamPacketData;
    public static DisplayPacketData displayPacketData;

    private final BukkitBridgePlayer player;
    @Getter private final Map<String, String> expectedTeams = new ConcurrentHashMap<>();

    static {
        try {
            int minorVersion = BukkitReflection.getMinorVersion();
            Scoreboard = BukkitReflection.getClass("world.scores.Scoreboard", "Scoreboard");
            ScoreboardObjective = BukkitReflection.getClass("world.scores.Objective", "world.scores.ScoreboardObjective", "ScoreboardObjective");
            Class<?> IScoreboardCriteria = BukkitReflection.getClass(
                    "world.scores.criteria.ObjectiveCriteria", // Mojang mapped
                    "world.scores.criteria.IScoreboardCriteria", // Bukkit 1.17.+
                    "IScoreboardCriteria", // 1.5.1 - 1.16.5
                    "IObjective" // 1.5.0
            );
            ObjectivePacketClass = BukkitReflection.getClass(
                    "network.protocol.game.ClientboundSetObjectivePacket", // Mojang mapped
                    "network.protocol.game.PacketPlayOutScoreboardObjective", // Bukkit 1.17+
                    "PacketPlayOutScoreboardObjective", // 1.7 - 1.16.5
                    "Packet206SetScoreboardObjective" // 1.5 - 1.6.4
            );
            emptyScoreboard = Scoreboard.getConstructor().newInstance();
            newObjectivePacket = ObjectivePacketClass.getConstructor(ScoreboardObjective, int.class);
            IScoreboardCriteria_dummy = ReflectionUtils.getFields(IScoreboardCriteria, IScoreboardCriteria).get(0).get(null);
            newScoreboardObjective = ReflectionUtils.getOnlyConstructor(ScoreboardObjective);
            if (minorVersion >= 7) {
                Component = BukkitReflection.getClass("network.chat.Component", "network.chat.IChatBaseComponent", "IChatBaseComponent");
            }
            if (minorVersion >= 8) {
                Class<?> EnumScoreboardHealthDisplay = BukkitReflection.getClass(
                        "world.scores.criteria.ObjectiveCriteria$RenderType",
                        "world.scores.criteria.IScoreboardCriteria$EnumScoreboardHealthDisplay",
                        "IScoreboardCriteria$EnumScoreboardHealthDisplay",
                        "EnumScoreboardHealthDisplay");
                healthDisplays = (Enum<?>[]) EnumScoreboardHealthDisplay.getMethod("values").invoke(null);
                if (minorVersion < 13) {
                    Objective_RENDER_TYPE = ReflectionUtils.getOnlyField(ObjectivePacketClass, EnumScoreboardHealthDisplay);
                }
            }
            if (minorVersion < 13) {
                ScoreboardObjective_setDisplayName = ReflectionUtils.getOnlyMethod(ScoreboardObjective, void.class, String.class);
            }
            if (BukkitReflection.is1_20_3Plus()) {
                NumberFormat = BukkitReflection.getClass("network.chat.numbers.NumberFormat");
                newFixedFormat = BukkitReflection.getClass("network.chat.numbers.FixedFormat").getConstructor(Component);
            }
            scorePacketData = new ScorePacketData();
            teamPacketData = new TeamPacketData();
            displayPacketData = new DisplayPacketData();
            available = true;
        } catch (Exception ignored) {
            // Print exception to find out what went wrong
        }
    }

    @Override
    @SneakyThrows
    public void setDisplaySlot(int slot, @NotNull String objective) {
        if (!available) return;
        player.sendPacket(displayPacketData.setDisplaySlot(slot, newObjective(objective, "", 0, null)));
    }

    @Override
    @SneakyThrows
    public void registerObjective(@NotNull String objectiveName, @NotNull String title, int display,
                                   @Nullable String numberFormat) {
        if (!available) return;
        player.sendPacket(newObjectivePacket(ObjectiveAction.REGISTER, objectiveName, title, display, numberFormat));
    }

    @Override
    public void unregisterObjective(@NotNull String objectiveName) {
        if (!available) return;
        player.sendPacket(newObjectivePacket(ObjectiveAction.UNREGISTER, objectiveName, "", 0, null));
    }

    @Override
    public void updateObjective(@NotNull String objectiveName, @NotNull String title, int display,
                                 @Nullable String numberFormat) {
        if (!available) return;
        player.sendPacket(newObjectivePacket(ObjectiveAction.UPDATE, objectiveName, title, display, numberFormat));
    }

    @SneakyThrows
    private Object newObjectivePacket(int action, @NotNull String objectiveName, @NotNull String title, int display,
                                      @Nullable String numberFormat) {
        Object packet = newObjectivePacket.newInstance(newObjective(objectiveName, title, display, numberFormat), action);
        if (BukkitReflection.getMinorVersion() >= 8 && BukkitReflection.getMinorVersion() < 13) {
            Objective_RENDER_TYPE.set(packet, healthDisplays[display]);
        }
        return packet;
    }

    @Override
    @SneakyThrows
    public void registerTeam(@NotNull String name, @NotNull String prefix, @NotNull String suffix,
                              @NotNull String visibility, @NotNull String collision,
                              @NotNull Collection<String> players, int options, int color) {
        if (!available) return;
        player.sendPacket(teamPacketData.registerTeam(name, prefix, toComponent(prefix), suffix,
                toComponent(suffix), visibility, collision, players, options, color));
        players.forEach(player -> expectedTeams.put(player, name));
    }

    @Override
    @SneakyThrows
    public void unregisterTeam(@NotNull String name) {
        if (!available) return;
        player.sendPacket(teamPacketData.unregisterTeam(name));
        expectedTeams.keySet().forEach(p -> expectedTeams.remove(p, name));
    }

    @Override
    @SneakyThrows
    public void updateTeam(@NotNull String name, @NotNull String prefix, @NotNull String suffix,
                            @NotNull String visibility, @NotNull String collision, int options, int color) {
        if (!available) return;
        player.sendPacket(teamPacketData.updateTeam(name, prefix, toComponent(prefix), suffix,
                toComponent(suffix), visibility, collision, options, color));
    }

    @Override
    public void setScore(@NotNull String objective, @NotNull String scoreHolder, int score,
                          @Nullable String displayName, @Nullable String numberFormat) {
        if (!available) return;
        player.sendPacket(scorePacketData.setScore(objective, scoreHolder, score, deserialize(displayName), toFixedFormat(numberFormat)));
    }

    @Override
    public void removeScore(@NotNull String objective, @NotNull String scoreHolder) {
        if (!available) return;
        player.sendPacket(scorePacketData.removeScore(objective, scoreHolder));
    }

    /**
     * Creates a new Scoreboard Objective with given parameters.
     *
     * @param   objectiveName
     *          Objective name
     * @param   title
     *          Objective title
     * @param   renderType
     *          Render type
     * @param   numberFormat
     *          Default number format (1.20.3+)
     * @return  Created objective
     */
    @SneakyThrows
    public Object newObjective(@NotNull String objectiveName, @NotNull String title, int renderType,
                               @Nullable String numberFormat) {
        if (BukkitReflection.is1_20_3Plus()) {
            // 1.20.3+
            return newScoreboardObjective.newInstance(
                    emptyScoreboard,
                    objectiveName,
                    null, // Criteria
                    toComponent(title),
                    healthDisplays[renderType],
                    false, // Auto update
                    toFixedFormat(numberFormat)
            );
        }
        if (BukkitReflection.getMinorVersion() >= 13) {
            // 1.13 - 1.20.2
            return newScoreboardObjective.newInstance(
                    emptyScoreboard,
                    objectiveName,
                    null, // Criteria
                    toComponent(title),
                    healthDisplays[renderType]
            );
        }
        // 1.5 - 1.12.2
        Object objective = newScoreboardObjective.newInstance(emptyScoreboard, objectiveName, IScoreboardCriteria_dummy);
        ScoreboardObjective_setDisplayName.invoke(objective, title);
        return objective;
    }

    @Nullable
    private Object toComponent(@Nullable String text) {
        if (text == null) return null;
        return toComponent(IChatBaseComponent.optimizedComponent(text));
    }

    @SneakyThrows
    private Object deserialize(@Nullable String json) {
        if (json == null) return null;
        return NMSStorage.getInstance().deserialize.apply(json);
    }

    @Nullable
    @SneakyThrows
    private Object toComponent(@Nullable IChatBaseComponent component) {
        if (component == null) return null;
        return deserialize(component.toString(player.getProtocolVersion()));
    }

    @Nullable
    @SneakyThrows
    private Object toFixedFormat(@Nullable String numberFormat) {
        if (numberFormat == null || newFixedFormat == null) return null;
        return newFixedFormat.newInstance(deserialize(numberFormat));
    }

    private static class ScorePacketData {

        private Constructor<?> newSetScorePacket;
        private Constructor<?> newResetScorePacket;      // 1.20.3+
        private Constructor<?> newSetScorePacket_String; // 1.12-
        private Constructor<?> newScoreboardScore;       // 1.12-
        private Field SetScorePacket_SCORE;              // 1.12-
        private Enum<?>[] scoreboardActions;             // 1.20.2-
        boolean is1_20_5Plus;

        @SneakyThrows
        public ScorePacketData() {
            Class<?> SetScorePacket = BukkitReflection.getClass(
                    "network.protocol.game.ClientboundSetScorePacket", // Mojang mapped
                    "network.protocol.game.PacketPlayOutScoreboardScore", // Bukkit 1.17+
                    "PacketPlayOutScoreboardScore", // 1.7 - 1.16.5
                    "Packet207SetScoreboardScore" // 1.5 - 1.6.4
            );
            if (BukkitReflection.is1_20_3Plus()) {
                try {
                    // 1.20.5+
                    newSetScorePacket = SetScorePacket.getConstructor(String.class, String.class, int.class, Optional.class, Optional.class);
                    is1_20_5Plus = true;
                } catch (Exception e) {
                    // 1.20.3 / 1.20.4
                    newSetScorePacket = SetScorePacket.getConstructor(String.class, String.class, int.class, Component, NumberFormat);
                }
                newResetScorePacket = BukkitReflection.getClass("network.protocol.game.ClientboundResetScorePacket").getConstructor(String.class, String.class);
            } else if (BukkitReflection.getMinorVersion() >= 13) {
                Class<?> EnumScoreboardAction = BukkitReflection.getClass("server.ServerScoreboard$Method",
                        "server.ScoreboardServer$Action", "ScoreboardServer$Action");
                newSetScorePacket = SetScorePacket.getConstructor(EnumScoreboardAction, String.class, String.class, int.class);
                scoreboardActions = (Enum<?>[]) EnumScoreboardAction.getMethod("values").invoke(null);
            } else {
                Class<?> ScoreboardScore = BukkitReflection.getClass("ScoreboardScore");
                newSetScorePacket_String = SetScorePacket.getConstructor(String.class);
                SetScorePacket_SCORE = ReflectionUtils.getFields(SetScorePacket, int.class).get(0);
                newScoreboardScore = ScoreboardScore.getConstructor(Scoreboard, ScoreboardObjective, String.class);
                if (BukkitReflection.getMinorVersion() >= 8) {
                    newSetScorePacket = SetScorePacket.getConstructor(ScoreboardScore);
                } else {
                    newSetScorePacket = SetScorePacket.getConstructor(ScoreboardScore, int.class);
                }
            }
        }

        @SneakyThrows
        public Object setScore(@NotNull String objective, @NotNull String scoreHolder, int score,
                               @Nullable Object displayName, @Nullable Object numberFormat) {
            if (is1_20_5Plus) {
                return newSetScorePacket.newInstance(scoreHolder, objective, score, Optional.ofNullable(displayName), Optional.ofNullable(numberFormat));
            } else if (BukkitReflection.is1_20_3Plus()) {
                return newSetScorePacket.newInstance(scoreHolder, objective, score, displayName, numberFormat);
            } else if (BukkitReflection.getMinorVersion() >= 13) {
                return newSetScorePacket.newInstance(scoreboardActions[0], objective, scoreHolder, score);
            } else {
                Object scoreboardScore = newScoreboardScore.newInstance(
                        emptyScoreboard,
                        newScoreboardObjective.newInstance(emptyScoreboard, objective, IScoreboardCriteria_dummy),
                        scoreHolder
                );
                Object packet;
                if (BukkitReflection.getMinorVersion() >= 8) {
                    packet = newSetScorePacket.newInstance(scoreboardScore);
                } else {
                    packet = newSetScorePacket.newInstance(scoreboardScore, ScoreAction.CHANGE);
                }
                SetScorePacket_SCORE.set(packet, score);
                return packet;
            }
        }

        @SneakyThrows
        public Object removeScore(@NotNull String objective, @NotNull String scoreHolder) {
            if (BukkitReflection.is1_20_3Plus()) {
                return newResetScorePacket.newInstance(scoreHolder, objective);
            } else if (BukkitReflection.getMinorVersion() >= 13) {
                return newSetScorePacket.newInstance(scoreboardActions[1], objective, scoreHolder, 0);
            } else {
                return newSetScorePacket_String.newInstance(scoreHolder);
            }
        }
    }

    public static class TeamPacketData {

        public Class<?> TeamPacketClass;
        private Constructor<?> newTeamPacket;
        private final Constructor<?> newScoreboardTeam;
        private Method TeamPacketConstructor_of;
        private Method TeamPacketConstructor_ofBoolean;
        public Field TeamPacket_NAME;
        public Field TeamPacket_ACTION;
        public Field TeamPacket_PLAYERS;
        private final Method ScoreboardTeam_getPlayerNameSet;
        private Method ScoreboardTeam_setNameTagVisibility;
        private Method ScoreboardTeam_setCollisionRule;
        private final Method ScoreboardTeam_setPrefix;
        private final Method ScoreboardTeam_setSuffix;
        private Method ScoreboardTeam_setColor;
        private final Method ScoreboardTeam_setAllowFriendlyFire;
        private final Method ScoreboardTeam_setCanSeeFriendlyInvisibles;
        private final Enum<?>[] chatFormats;
        private Enum<?>[] nameVisibilities;
        private Enum<?>[] collisionRules;

        @SneakyThrows
        public TeamPacketData() {
            int minorVersion = BukkitReflection.getMinorVersion();
            Class<?> scoreboardTeam = BukkitReflection.getClass("world.scores.PlayerTeam", "world.scores.ScoreboardTeam", "ScoreboardTeam");
            Class<?> enumChatFormatClass = BukkitReflection.getClass("ChatFormatting", "EnumChatFormat", "EnumChatFormat");
            TeamPacketClass = BukkitReflection.getClass(
                    "network.protocol.game.ClientboundSetPlayerTeamPacket", // Mojang mapped
                    "network.protocol.game.PacketPlayOutScoreboardTeam", // Bukkit 1.17+
                    "PacketPlayOutScoreboardTeam", // Bukkit 1.7 - 1.16.5
                    "Packet209SetScoreboardTeam" // 1.5 - 1.6.4
            );
            newScoreboardTeam = scoreboardTeam.getConstructor(Scoreboard, String.class);
            TeamPacket_NAME = ReflectionUtils.getFields(TeamPacketClass, String.class).get(0);
            TeamPacket_ACTION = ReflectionUtils.getInstanceFields(TeamPacketClass, int.class).get(0);
            TeamPacket_PLAYERS = ReflectionUtils.getOnlyField(TeamPacketClass, Collection.class);
            ScoreboardTeam_getPlayerNameSet = ReflectionUtils.getOnlyMethod(scoreboardTeam, Collection.class);
            chatFormats = (Enum<?>[]) enumChatFormatClass.getMethod("values").invoke(null);
            ScoreboardTeam_setAllowFriendlyFire = ReflectionUtils.getMethod(
                    scoreboardTeam,
                    new String[] {"func_96660_a", "setAllowFriendlyFire", "a", "m_83355_"}, // {Thermos, 1.5.1+, 1.5 & 1.18+, Mohist 1.18.2}
                    boolean.class
            );
            ScoreboardTeam_setCanSeeFriendlyInvisibles = ReflectionUtils.getMethod(
                    scoreboardTeam,
                    new String[] {"func_98300_b", "setCanSeeFriendlyInvisibles", "b", "m_83362_", "setSeeFriendlyInvisibles"}, // {Thermos, 1.5.1+, 1.5 & 1.18+, Mohist 1.18.2, 1.20.2+}
                    boolean.class
            );
            if (minorVersion >= 8) {
                Class<?> enumNameTagVisibility = BukkitReflection.getClass(
                        "world.scores.Team$Visibility", // Mojang mapped
                        "world.scores.ScoreboardTeamBase$EnumNameTagVisibility", // Bukkit 1.17+
                        "ScoreboardTeamBase$EnumNameTagVisibility", // Bukkit 1.8.1 - 1.16.5
                        "EnumNameTagVisibility" // Bukkit 1.8.0
                );
                nameVisibilities = (Enum<?>[]) enumNameTagVisibility.getMethod("values").invoke(null);
                ScoreboardTeam_setNameTagVisibility = ReflectionUtils.getMethod(
                        scoreboardTeam,
                        new String[] {"setNameTagVisibility", "a", "m_83346_"}, // {1.8.1+, 1.8 & 1.18+, Mohist 1.18.2}
                        enumNameTagVisibility
                );
            }
            if (minorVersion >= 9) {
                Class<?> enumTeamPush = BukkitReflection.getClass("world.scores.Team$CollisionRule",
                        "world.scores.ScoreboardTeamBase$EnumTeamPush", "ScoreboardTeamBase$EnumTeamPush");
                ScoreboardTeam_setCollisionRule = ReflectionUtils.getOnlyMethod(scoreboardTeam, void.class, enumTeamPush);
                collisionRules = (Enum<?>[]) enumTeamPush.getMethod("values").invoke(null);
            }
            if (minorVersion >= 13) {
                ScoreboardTeam_setColor = ReflectionUtils.getOnlyMethod(scoreboardTeam, void.class, enumChatFormatClass);
                ScoreboardTeam_setPrefix = ReflectionUtils.getMethod(
                        scoreboardTeam,
                        new String[]{"setPrefix", "b", "m_83360_", "setPlayerPrefix"}, // {1.17.1-, 1.18 - 1.20.1, Mohist 1.18.2, 1.20.2+}
                        Component
                );
                ScoreboardTeam_setSuffix = ReflectionUtils.getMethod(
                        scoreboardTeam,
                        new String[]{"setSuffix", "c", "m_83365_", "setPlayerSuffix"}, // {1.17.1-, 1.18 - 1.20.1, Mohist 1.18.2, 1.20.2+}
                        Component
                );
            } else {
                ScoreboardTeam_setPrefix = ReflectionUtils.getMethod(
                        scoreboardTeam,
                        new String[] {"func_96666_b", "setPrefix", "b"}, // {Thermos, 1.5.1+, 1.5}
                        String.class
                );
                ScoreboardTeam_setSuffix = ReflectionUtils.getMethod(
                        scoreboardTeam,
                        new String[] {"func_96662_c", "setSuffix", "c"}, // {Thermos, 1.5.1+, 1.5}
                        String.class
                );
            }
            if (minorVersion >= 17) {
                TeamPacketConstructor_of = ReflectionUtils.getOnlyMethod(TeamPacketClass, TeamPacketClass, scoreboardTeam);
                TeamPacketConstructor_ofBoolean = ReflectionUtils.getOnlyMethod(TeamPacketClass, TeamPacketClass, scoreboardTeam, boolean.class);
            } else {
                newTeamPacket = TeamPacketClass.getConstructor(scoreboardTeam, int.class);
            }
        }

        @SuppressWarnings("unchecked")
        @SneakyThrows
        public Object registerTeam(@NotNull String name, @NotNull String prefix, @Nullable Object prefixComponent,
                                   @NotNull String suffix, @Nullable Object suffixComponent,
                                   @NotNull String visibility, @NotNull String collision,
                                   @NotNull Collection<String> players, int options, int color) {
            Object team = createTeam(name, prefix, prefixComponent, suffix, suffixComponent, visibility, collision, options, color);
            ((Collection<String>)ScoreboardTeam_getPlayerNameSet.invoke(team)).addAll(players);
            if (BukkitReflection.getMinorVersion() >= 17) {
                return TeamPacketConstructor_ofBoolean.invoke(null, team, true);
            } else {
                return newTeamPacket.newInstance(team, TeamAction.CREATE);
            }
        }

        @SneakyThrows
        public Object unregisterTeam(@NotNull String name) {
            Object team = newScoreboardTeam.newInstance(emptyScoreboard, name);
            if (BukkitReflection.getMinorVersion() >= 17) {
                return TeamPacketConstructor_of.invoke(null, team);
            } else {
                return newTeamPacket.newInstance(team, TeamAction.REMOVE);
            }
        }

        @SneakyThrows
        public Object updateTeam(@NotNull String name, @NotNull String prefix, @Nullable Object prefixComponent,
                                 @NotNull String suffix, @Nullable Object suffixComponent,
                                 @NotNull String visibility, @NotNull String collision, int options, int color) {
            Object team = createTeam(name, prefix, prefixComponent, suffix, suffixComponent, visibility, collision, options, color);
            if (BukkitReflection.getMinorVersion() >= 17) {
                return TeamPacketConstructor_ofBoolean.invoke(null, team, false);
            } else {
                return newTeamPacket.newInstance(team, TeamAction.UPDATE);
            }
        }

        @SneakyThrows
        private Object createTeam(@NotNull String teamName, @NotNull String prefix, @Nullable Object prefixComponent,
                                  @NotNull String suffix, @Nullable Object suffixComponent,
                                  @NotNull String visibility, @NotNull String collision, int options, int color) {
            Object team = newScoreboardTeam.newInstance(emptyScoreboard, teamName);
            ScoreboardTeam_setAllowFriendlyFire.invoke(team, (options & 0x1) > 0);
            ScoreboardTeam_setCanSeeFriendlyInvisibles.invoke(team, (options & 0x2) > 0);
            if (BukkitReflection.getMinorVersion() >= 13) {
                ScoreboardTeam_setPrefix.invoke(team, prefixComponent);
                ScoreboardTeam_setSuffix.invoke(team, suffixComponent);
                ScoreboardTeam_setColor.invoke(team, chatFormats[color]);
            } else {
                ScoreboardTeam_setPrefix.invoke(team, prefix);
                ScoreboardTeam_setSuffix.invoke(team, suffix);
            }
            if (BukkitReflection.getMinorVersion() >= 8)
                ScoreboardTeam_setNameTagVisibility.invoke(team, nameVisibilities[NameVisibility.getByName(visibility).ordinal()]);
            if (BukkitReflection.getMinorVersion() >= 9)
                ScoreboardTeam_setCollisionRule.invoke(team, collisionRules[CollisionRule.getByName(collision).ordinal()]);
            return team;
        }
    }

    public static class DisplayPacketData {

        public final Class<?> DisplayObjectiveClass;
        private final Constructor<?> newDisplayObjective;
        private final Object[] displaySlots;

        @SneakyThrows
        public DisplayPacketData() {
            DisplayObjectiveClass = BukkitReflection.getClass(
                    "network.protocol.game.ClientboundSetDisplayObjectivePacket", // Mojang mapped
                    "network.protocol.game.PacketPlayOutScoreboardDisplayObjective", // Bukkit 1.17+
                    "PacketPlayOutScoreboardDisplayObjective", // Bukkit 1.7 - 1.16.5
                    "Packet208SetScoreboardDisplayObjective" // Bukkit 1.5 - 1.6.4
            );
            if (BukkitReflection.is1_20_2Plus()) {
                Class<?> DisplaySlot = BukkitReflection.getClass("world.scores.DisplaySlot");
                displaySlots = (Object[]) DisplaySlot.getDeclaredMethod("values").invoke(null);
                newDisplayObjective = DisplayObjectiveClass.getConstructor(DisplaySlot, ScoreboardObjective);
            } else {
                displaySlots = new Object[]{0, 1, 2};
                newDisplayObjective = DisplayObjectiveClass.getConstructor(int.class, ScoreboardObjective);
            }
        }

        @SneakyThrows
        public Object setDisplaySlot(int slot, @NotNull Object objective) {
            return newDisplayObjective.newInstance(displaySlots[slot], objective);
        }
    }
}
