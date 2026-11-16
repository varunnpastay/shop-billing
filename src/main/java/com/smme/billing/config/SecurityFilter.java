package com.smme.billing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * Adds standard security headers and blocks cross-site form posts (CSRF):
 * a POST whose Origin/Referer points at a different host than this server is rejected.
 */
@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "SAMEORIGIN");
        res.setHeader("Referrer-Policy", "same-origin");

        if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod()) && !"OPTIONS".equals(req.getMethod())) {
            String source = req.getHeader("Origin");
            if (source == null || source.isBlank() || "null".equals(source)) source = req.getHeader("Referer");
            if (source != null && !source.isBlank() && !sameHost(source, req)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site request blocked");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean sameHost(String url, HttpServletRequest req) {
        try {
            URI u = URI.create(url.trim());
            String origin = u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
            String host = req.getHeader("Host");
            if (host == null) host = req.getServerName() + ":" + req.getServerPort();
            if (host.equalsIgnoreCase(origin) || host.equalsIgnoreCase(u.getHost())) return true;
            // behind a proxy (Tailscale serve, Caddy) the address the browser used may arrive as X-Forwarded-Host
            String fwd = req.getHeader("X-Forwarded-Host");
            if (fwd == null || fwd.isBlank()) return false;
            fwd = fwd.split(",")[0].trim();
            return fwd.equalsIgnoreCase(origin) || fwd.equalsIgnoreCase(u.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
