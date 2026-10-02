package dev.lightlogin.core.util;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;

/**
 * A parsed, DNS-free IP address with CIDR matching.
 *
 * <p>Parsing goes through {@link InetAddress#ofLiteral} which never performs a lookup, so an
 * attacker cannot induce a DNS resolution (and therefore a delay or an SSRF) by connecting with a
 * crafted hostname. Addresses are normalised so that the many textual spellings of the same IPv6
 * address collapse to one key for ban lists and rate-limit buckets.</p>
 */
public final class IpAddress {

    private final InetAddress address;
    private final byte[] bytes;

    private IpAddress(InetAddress address) {
        this.address = address;
        this.bytes = address.getAddress();
    }

    /**
     * Parses an IP literal.
     *
     * @throws IllegalArgumentException when the input is not a valid IPv4 or IPv6 literal
     */
    public static IpAddress of(String literal) {
        Objects.requireNonNull(literal, "literal");
        try {
            return new IpAddress(InetAddress.ofLiteral(literal.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid IP literal: " + literal, e);
        }
    }

    /** Parses, returning {@code null} instead of throwing for untrusted input. */
    public static IpAddress ofOrNull(String literal) {
        if (literal == null || literal.isBlank()) {
            return null;
        }
        try {
            return of(literal);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The raw address bytes (4 for IPv4, 16 for IPv6). */
    public byte[] bytes() {
        return bytes.clone();
    }

    public boolean isIpv4() {
        return bytes.length == 4;
    }

    public boolean isLoopback() {
        return address.isLoopbackAddress();
    }

    /** Whether the address is in a private / non-routable range (RFC 1918, RFC 4193, link-local). */
    public boolean isPrivate() {
        if (isIpv4()) {
            int a = bytes[0] & 0xFF;
            int b = bytes[1] & 0xFF;
            return a == 10
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168)
                    || (a == 127)
                    || (a == 169 && b == 254);
        }
        int first = bytes[0] & 0xFF;
        return (first & 0xFE) == 0xFC // fc00::/7 unique local
                || (bytes[0] == (byte) 0xFE && (bytes[1] & 0xC0) == 0x80); // fe80::/10 link local
    }

    /**
     * Tests membership of a CIDR block such as {@code 203.0.113.0/24} or {@code 2001:db8::/32}.
     * A bare address (no slash) matches only itself.
     */
    public boolean isInRange(String cidr) {
        Objects.requireNonNull(cidr, "cidr");
        String trimmed = cidr.trim();
        int slash = trimmed.indexOf('/');
        if (slash < 0) {
            IpAddress other = ofOrNull(trimmed);
            return other != null && Arrays.equals(bytes, other.bytes);
        }
        String networkLiteral = trimmed.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(trimmed.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        IpAddress network = ofOrNull(networkLiteral);
        if (network == null || network.bytes.length != bytes.length) {
            return false;
        }
        if (prefix < 0 || prefix > bytes.length * 8) {
            return false;
        }
        return samePrefix(network.bytes, bytes, prefix);
    }

    private static boolean samePrefix(byte[] a, byte[] b, int prefixBits) {
        int fullBytes = prefixBits / 8;
        int remainder = prefixBits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        if (remainder == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainder) & 0xFF;
        return (a[fullBytes] & mask) == (b[fullBytes] & mask);
    }

    /** Canonical textual form, used as a stable map key. */
    @Override
    public String toString() {
        return address.getHostAddress();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IpAddress other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}