package org.sibyl.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

public class SibylFirstLoginFilter extends OncePerRequestFilter {
    private final SibylAccounts accounts;
    public SibylFirstLoginFilter(SibylAccounts accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/")
            && !path.equals("/api/v1/admin/csrf")
            && !path.equals("/api/v1/admin/account")
            && !path.equals("/api/v1/admin/account/password")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && accounts.mustChangePassword(auth.getName())) {
                response.setStatus(423);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"password_change_required\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
