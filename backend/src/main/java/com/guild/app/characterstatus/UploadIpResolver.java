package com.guild.app.characterstatus;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/** Only numeric addresses and explicitly trusted proxy hops; never resolve hostnames from headers. */
@Component
public class UploadIpResolver {
    private final List<IpAddressMatcher> proxies;

    public UploadIpResolver(@Value("${guild.dll.trusted-proxies:127.0.0.1/32,::1/128}") String proxies) {
        this.proxies = Arrays.stream(proxies.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        InetAddress address = numeric(request.getRemoteAddr());
        if (address == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (trusted(address) && forwarded != null) {
            if (forwarded.length() > 1024) return null;
            String[] hops = forwarded.split(",", -1);
            if (hops.length > 16) return null;
            // Walk from our proxy towards the client; ignore client-supplied prefixes beyond it.
            for (int i = hops.length - 1; i >= 0 && trusted(address); i--) {
                address = numeric(hops[i].trim());
                if (address == null) return null;
            }
        }
        byte[] bytes = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()
                || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)) return null;
        return address.getHostAddress();
    }

    private boolean trusted(InetAddress address) {
        return proxies.stream().anyMatch(p -> p.matches(address.getHostAddress()));
    }

    private static InetAddress numeric(String value) {
        if (value == null || value.length() > 45 || !(value.matches("[0-9]+(\\.[0-9]+){3}")
                || (value.contains(":") && value.matches("[0-9a-fA-F:.]+")))) return null;
        try { return InetAddress.getByName(value); }
        catch (UnknownHostException ignored) { return null; }
    }
}
