package com.fishingtime.partygame.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Temporary ingress diagnostics for PartyGame WebSocket requests.
 * This filter only logs requests to /ws/partygame and does not modify them.
 */
@Component
public class PartyGameRequestTraceFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PartyGameRequestTraceFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/ws/partygame".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        log.info("[PartyGame-HTTP] IN method={}, uri={}, upgrade={}, connection={}, origin={}, remote={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("Upgrade"),
                request.getHeader("Connection"),
                request.getHeader("Origin"),
                request.getRemoteAddr());

        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("[PartyGame-HTTP] OUT method={}, uri={}, status={}, upgradeResponse={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    response.getHeader("Upgrade"));
        }
    }
}
