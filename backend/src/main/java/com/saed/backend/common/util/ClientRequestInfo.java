package com.saed.backend.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;

/**
 * Network origin of the current request for audit trails (F10-19), instead of a fixed "127.0.0.1". Outside a web
 * request (schedulers, tests) it reports the internal markers.
 */
public final class ClientRequestInfo {

    static final String INTERNAL_IP = "127.0.0.1";
    static final String INTERNAL_AGENT = "SAED-Core";
    private static final int MAX_AGENT_LENGTH = 255;

    private ClientRequestInfo() {
    }

    public static String ip() {
        HttpServletRequest request = currentRequest();
        return request == null ? INTERNAL_IP : clientIp(request);
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return INTERNAL_AGENT;
        }
        String agent = request.getHeader("User-Agent");
        if (agent == null || agent.isBlank()) {
            return INTERNAL_AGENT;
        }
        return agent.length() > MAX_AGENT_LENGTH ? agent.substring(0, MAX_AGENT_LENGTH) : agent;
    }

    /**
     * Client address. Behind a trusted reverse proxy (the direct peer has a loopback or private address) the entry
     * appended by that proxy, the last one of X-Forwarded-For, is used; entries before it are client-controlled.
     */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && isInternal(remote)) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty() && last.length() <= 45 && last.matches("[0-9a-fA-F:.]+")) {
                return last;
            }
        }
        return remote;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static boolean isInternal(String address) {
        try {
            if (!address.matches("[0-9a-fA-F:.]+")) {
                return false;
            }
            InetAddress parsed = InetAddress.getByName(address);
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress() || parsed.isLinkLocalAddress();
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
