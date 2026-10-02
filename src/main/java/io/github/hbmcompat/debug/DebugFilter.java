package io.github.hbmcompat.debug;

/** Per-player selection of diagnostic sources. */
public enum DebugFilter {
    ALL("all"), BUS("bus"), ADAPTER("adapter");

    public final String argument;

    DebugFilter(String argument) { this.argument = argument; }

    public boolean accepts(DebugFilter category) { return this == ALL || this == category; }

    public static DebugFilter parse(String argument) {
        for (DebugFilter filter : values()) if (filter.argument.equals(argument)) return filter;
        return null;
    }
}
