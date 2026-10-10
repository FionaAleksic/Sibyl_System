package org.sibyl.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** New accounts may only rotate the initial password until that is completed. */
public class SibylFirstLoginFilter extends OncePerRequestFilter {
    private final SibylAccounts accounts;
    public SibylFirstLoginFilter(SibylAccounts accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean allowed = path.equals("/login.html") || path.equals("/login.js")
            || path.equals("/login.css") || path.equals("/login")
            || path.equals("/logout") || path.equals("/actuator/health")
            || path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/csrf")
            || path.equals("/api/v1/auth/password");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!allowed && auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)
            && accounts.mustChangePassword(auth.getName())) {
            res.setHeader("Cache-Control", "no-store");
            if (path.startsWith("/api/")) {
                res.setStatus(423);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"password_change_required\"}");
            } else {
                res.sendRedirect("/login.html?change=1");
            }
            return;
        }
        chain.doFilter(req, res);
    }
}
