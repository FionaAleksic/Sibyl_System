package org.sibyl.core;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Default deny: only the login form and CSRF bootstrap are public. */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, SibylAccounts accounts) throws Exception {
        http
            .authorizeHttpRequests(registry -> registry
                .requestMatchers("/login.html", "/login.css", "/login.js",
                                 "/login", "/api/v1/auth/csrf", "/actuator/health").permitAll()
                .requestMatchers("/preview/**").denyAll()
                .requestMatchers("/admin.html", "/admin.js", "/addons.html",
                                 "/addons.js", "/catalog.json", "/api/v1/admin/**",
                                 "/api/v1/addons/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/**", "/api/v1/settings/public",
                                 "/", "/index.html", "/sibyl.js", "/sibyl.css").authenticated()
                .requestMatchers("/api/**").denyAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, exception) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .permitAll())
            .logout(logout -> logout.logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .clearAuthentication(true))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .exceptionHandling(handler -> handler
                .authenticationEntryPoint((request, response, error) -> {
                    if (request.getRequestURI().startsWith("/api/"))
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    else
                        response.sendRedirect("/login.html");
                })
                .accessDeniedHandler((request, response, error) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)))
            .addFilterAfter(new SibylFirstLoginFilter(accounts),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
