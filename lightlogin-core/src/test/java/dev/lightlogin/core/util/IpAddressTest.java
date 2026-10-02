package dev.lightlogin.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpAddressTest {

    @Test
    @DisplayName("IPv4 literals parse and normalise")
    void ipv4() {
        IpAddress address = IpAddress.of("203.0.113.7");
        assertTrue(address.isIpv4());
        assertEquals("203.0.113.7", address.toString());
        assertEquals(4, address.bytes().length);
    }

    @Test
    @DisplayName("IPv6 literals parse; equivalent spellings normalise to the same key")
    void ipv6Normalisation() {
        IpAddress full = IpAddress.of("2001:0db8:0000:0000:0000:0000:0000:0001");
        IpAddress shortForm = IpAddress.of("2001:db8::1");
        assertEquals(full, shortForm);
        assertEquals(full.hashCode(), shortForm.hashCode());
    }

    @Test
    @DisplayName("a hostname is rejected rather than resolved (no DNS on the join path)")
    void hostnamesRejected() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of("example.com"));
        assertNull(IpAddress.ofOrNull("not an ip"));
        assertNull(IpAddress.ofOrNull(""));
        assertNull(IpAddress.ofOrNull(null));
    }

    @Test
    @DisplayName("CIDR matching works for IPv4 and IPv6, including the boundary addresses")
    void cidrMatching() {
        IpAddress inside = IpAddress.of("203.0.113.55");
        assertTrue(inside.isInRange("203.0.113.0/24"));
        assertTrue(inside.isInRange("203.0.113.55"));
        assertFalse(inside.isInRange("203.0.114.0/24"));
        assertFalse(inside.isInRange("203.0.113.0/32"));

        IpAddress v6 = IpAddress.of("2001:db8:abcd::5");
        assertTrue(v6.isInRange("2001:db8::/32"));
        assertFalse(v6.isInRange("2001:dead::/32"));
    }

    @Test
    @DisplayName("a /0 block matches everything of the same family")
    void matchAll() {
        assertTrue(IpAddress.of("8.8.8.8").isInRange("0.0.0.0/0"));
        assertFalse(IpAddress.of("8.8.8.8").isInRange("2001:db8::/32"));
    }

    @Test
    @DisplayName("loopback and private ranges are recognised")
    void classification() {
        assertTrue(IpAddress.of("127.0.0.1").isLoopback());
        assertTrue(IpAddress.of("10.1.2.3").isPrivate());
        assertTrue(IpAddress.of("192.168.1.1").isPrivate());
        assertTrue(IpAddress.of("172.16.0.1").isPrivate());
        assertFalse(IpAddress.of("172.32.0.1").isPrivate());
        assertFalse(IpAddress.of("8.8.8.8").isPrivate());
        assertTrue(IpAddress.of("fd00::1").isPrivate());
    }

    @Test
    @DisplayName("malformed CIDR prefixes do not match and do not throw")
    void malformedCidr() {
        IpAddress address = IpAddress.of("1.2.3.4");
        assertFalse(address.isInRange("1.2.3.0/abc"));
        assertFalse(address.isInRange("1.2.3.0/999"));
        assertFalse(address.isInRange("1.2.3.0/-1"));
    }
}