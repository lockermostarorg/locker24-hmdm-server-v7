package com.hmdm.plugins.locker24health.settings;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public final class NetworkGuard {

    private static final Pattern NUMERIC_HOST =
            Pattern.compile("^(0[xX][0-9a-fA-F]+|[0-9]+)(\\.(0[xX][0-9a-fA-F]+|[0-9]+))*$");

    private NetworkGuard() {
    }

    public static boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    public static boolean isBlockedHost(String host) {
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        String lower = h.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost")) {
            return true;
        }
        boolean literal = h.indexOf(':') >= 0 || NUMERIC_HOST.matcher(h).matches();
        if (!literal) {
            return false;
        }
        try {
            return isBlocked(InetAddress.getByName(h));
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
