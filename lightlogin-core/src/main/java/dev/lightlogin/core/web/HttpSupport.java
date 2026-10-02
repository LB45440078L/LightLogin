package dev.lightlogin.core.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small HTTP helpers for the admin panel.
 *
 * <p>Every response passes through {@link #securityHeaders()} so the panel ships with a strict
 * Content-Security-Policy, frame denial and MIME sniffing protection by default. Form parsing is
 * bounded in size, which caps the cost of a malicious request body.</p>
 */
public final class HttpSupport {

    /** Maximum accepted request body, in bytes. */
    private static final int MAX_BODY = 64 * 1024;

    private HttpSupport() {
    }

    /** Escapes text for safe insertion into HTML. */
    public static String escape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Reads a single query-string parameter. */
    public static String queryParam(URI uri, String name) {
        Map<String, String> params = parseForm(uri.getRawQuery());
        return params.get(name);
    }

    /** Parses a URL-encoded string (query or body) into a map, last value winning. */
    public static Map<String, String> parseForm(String encoded) {
        Map<String, String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    /** Reads and parses a url-encoded request body, bounded by {@link #MAX_BODY}. */
    public static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY);
            return parseForm(new String(body, StandardCharsets.UTF_8));
        }
    }

    /** Reads a cookie value by name. */
    public static String cookie(HttpExchange exchange, String name) {
        java.util.List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).equals(name)) {
                    return trimmed.substring(eq + 1);
                }
            }
        }
        return null;
    }

    /** The client address, honouring proxy headers only when explicitly trusted. */
    public static String clientIp(HttpExchange exchange, boolean trustProxyHeaders) {
        if (trustProxyHeaders) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    /** Standard hardening headers. */
    public static Map<String, String> securityHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("X-Frame-Options", "DENY");
        headers.put("Referrer-Policy", "no-referrer");
        headers.put("Cache-Control", "no-store");
        return headers;
    }

    /** Sends an HTML response. */
    public static void sendHtml(HttpExchange exchange, int status, String html,
                                Map<String, String> extraHeaders) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = securityHeaders();
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        headers.forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** Sends a redirect. */
    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        securityHeaders().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    /** Sends a plain-text error. */
    public static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        securityHeaders().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}