package org.sibyl.core;

import java.util.List;

/** Numeric-only RFC1918 subnet matching. No DNS or implicit discovery. */
public final class IPv4Scope {
    private IPv4Scope() {}
    public static long ip(String address) {
        if (address == null || !address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))
            throw new IllegalArgumentException("IPv4 address required");
        long value = 0;
        for (String part : address.split("\\.")) {
            if (part.length() > 1 && part.startsWith("0"))
                throw new IllegalArgumentException("Ambiguous IPv4 address");
            int n = Integer.parseInt(part);
            if (n > 255) throw new IllegalArgumentException("Bad IPv4 octet");
            value = (value << 8) | n;
        }
        return value;
    }
    public static boolean privateAddress(long ip) {
        return (ip & 0xff000000L) == 0x0a000000L
            || (ip & 0xfff00000L) == 0xac100000L
            || (ip & 0xffff0000L) == 0xc0a80000L;
    }
    public static String network(String cidr) {
        if (cidr == null || !cidr.matches("[0-9.]+/(?:1[6-9]|2[0-9]|30)"))
            throw new IllegalArgumentException("Only /16 through /30 permitted");
        String[] p = cidr.split("/");
        int bits = Integer.parseInt(p[1]);
        long base = ip(p[0]), mask = (0xffffffffL << (32-bits)) & 0xffffffffL;
        if (!privateAddress(base) || (base & mask) != base)
            throw new IllegalArgumentException("Aligned RFC1918 range required");
        return p[0]+"/"+bits;
    }
    public static boolean contains(String range, String address) {
        String[] p=network(range).split("/");
        long mask=(0xffffffffL << (32-Integer.parseInt(p[1]))) & 0xffffffffL;
        return (ip(address) & mask) == ip(p[0]);
    }
    public static boolean inside(List<String> networks, String address) {
        if (!privateAddress(ip(address))) return false;
        for (String range : networks) if (contains(range,address)) return true;
        return false;
    }
}
