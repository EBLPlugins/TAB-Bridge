package me.neznamy.tab.bridge.shared.placeholder;

import me.neznamy.tab.bridge.shared.BridgePlayer;
import me.neznamy.tab.bridge.shared.TABBridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class RelationalPlaceholder extends Placeholder {

    private final Map<BridgePlayer, Map<BridgePlayer, String>> lastValues = new WeakHashMap<>();
    private final BiFunction<BridgePlayer, BridgePlayer, String> function;

    public RelationalPlaceholder(String identifier, int refresh, BiFunction<BridgePlayer, BridgePlayer, String> function) {
        super(identifier, refresh);
        this.function = function;
    }

    public boolean update(BridgePlayer viewer, BridgePlayer target) {
        String value = request(viewer, target);
        if (!lastValues.computeIfAbsent(viewer, v -> new WeakHashMap<>()).getOrDefault(target, getIdentifier()).equals(value)) {
            lastValues.get(viewer).put(target, value);
            return true;
        }
        return false;
    }

    public String request(BridgePlayer viewer, BridgePlayer target) {
        long time = System.currentTimeMillis();
        try {
            return function.apply(viewer, target);
        } catch (Throwable t) {
            List<Object> args = new ArrayList<>();
            args.add("PlaceholderError");
            args.add("Relational placeholder " + identifier + " generated an error when setting for viewer " + viewer.getName() + " and target " + target.getName());
            args.add(t.getStackTrace().length+1);
            args.add(t.getClass().getName() + ": " + t.getMessage());
            args.addAll(Arrays.stream(t.getStackTrace()).map(e -> "\tat " + e.toString()).collect(Collectors.toList()));
            viewer.sendMessage(args.toArray());
            return "<PlaceholderAPI Error>";
        } finally {
            long timeDiff = System.currentTimeMillis() - time;
            if (timeDiff > 50) {
                TABBridge.getInstance().getPlatform().sendConsoleMessage("&c[WARN] Placeholder " + identifier + " took " + timeDiff + "ms to return value for " + viewer.getName() + " and " + target.getName());
            }
        }
    }

    public String getLastValue(BridgePlayer viewer, BridgePlayer target) {
        if (!lastValues.computeIfAbsent(viewer, v -> new WeakHashMap<>()).containsKey(target)) {
            update(viewer, target);
        }
        return lastValues.get(viewer).getOrDefault(target, getIdentifier());
    }
}
