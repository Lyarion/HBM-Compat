package io.github.hbmcompat.fluid;

import java.util.HashMap;
import java.util.Map;

/** Preferred outputs and unambiguous input aliases are intentionally separate. */
final class FluidMappingTable<T, F> {
    private final Map<T, F> preferred = new HashMap<T, F>();
    private final Map<String, T> canonical = new HashMap<String, T>();
    private final Map<String, T> aliases = new HashMap<String, T>();
    private final java.util.Set<String> ambiguous = new java.util.HashSet<String>();

    void prefer(T type, String name, F fluid) {
        T previous = canonical.get(name);
        if (previous != null && !previous.equals(type)) {
            throw new IllegalStateException("Conflicting HBM types mapped to Forge fluid '" + name
                    + "': " + previous + " and " + type + ". Check Bob's custom mappings.");
        }
        canonical.put(name, type);
        preferred.put(type, fluid);
    }

    boolean alias(T type, String name) {
        T owner = canonical.get(name);
        if (owner != null) return owner.equals(type);
        if (ambiguous.contains(name)) return false;
        owner = aliases.get(name);
        if (owner != null && !owner.equals(type)) {
            aliases.remove(name);
            ambiguous.add(name);
            return false;
        }
        aliases.put(name, type);
        return true;
    }

    F output(T type) { return preferred.get(type); }
    T input(String name) {
        T type = canonical.get(name);
        return type != null ? type : aliases.get(name);
    }
}
