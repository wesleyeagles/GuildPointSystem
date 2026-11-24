package com.guild.app.characterstatus;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class UploadIpResolverTest {
    private String resolve(String peer, String header, String proxies) {
        var r = new MockHttpServletRequest();r.setRemoteAddr(peer);
        if (header != null) r.addHeader("X-Forwarded-For", header);
        return new UploadIpResolver(proxies).resolve(r);
    }
    @Test void ignoresSpoofedHeadersFromDirectClients() {
        assertEquals("203.0.113.7", resolve("203.0.113.7", "198.51.100.99", "127.0.0.1/32"));
    }
    @Test void walksOnlyTrustedHopsFromRightToLeft() {
        assertEquals("203.0.113.7", resolve("172.18.0.2", "198.51.100.99, 203.0.113.7, 172.18.0.3", "172.18.0.0/16"));
    }
    @Test void doesNotGroupEveryoneUnderAnUnconfiguredProxyOrLocalAddress() {
        assertNull(resolve("172.18.0.2", "203.0.113.7", "127.0.0.1/32"));
        assertNull(resolve("127.0.0.1", null, "127.0.0.1/32"));
        assertNull(resolve("fd00::1", null, ""));
    }
    @Test void normalizesIpv6AndMappedIpv4() {
        assertEquals(resolve("2001:db8::1", null, ""), resolve("127.0.0.1", "2001:0db8:0:0:0:0:0:1", "127.0.0.1/32"));
        assertEquals("203.0.113.7", resolve("::ffff:203.0.113.7", null, ""));
    }
    @Test void rejectsNamesInvalidAddressesAndOversizedHeadersWithoutDns() {
        for (String value : new String[]{"example.com", "deadbeef", "999.1.1.1", "1.2.3.4:80", "203.0.113.7,", "x".repeat(1025)}) {
            assertNull(resolve("127.0.0.1", value, "127.0.0.1/32"), value);
        }
    }
}
