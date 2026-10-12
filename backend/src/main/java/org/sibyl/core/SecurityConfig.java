package org.sibyl.core;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Default deny with distinct API (401) and browser (login redirect) chains. */
@Configuration
public class SecurityConfig {
    /** API requests NEVER redirect to HTML, even without an Accept header. */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http, SibylAccounts accounts) throws Exception {
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(registry -> registry
                .requestMatchers("/api/v1/auth/csrf").permitAll()
                .requestMatchers("/api/v1/admin/**", "/api/v1/addons/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/**", "/api/v1/settings/public", "/api/v1/modules", "/api/v1/modules/**", "/api/v1/extensions").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, denied) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN)))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .addFilterAfter(new SibylFirstLoginFilter(accounts),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** The only anonymous website is /login.html plus its CSS/JS. */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurity(HttpSecurity http, SibylAccounts accounts) throws Exception {
        http.authorizeHttpRequests(registry -> registry
                .requestMatchers("/preview/**").denyAll()
                .requestMatchers("/login.html", "/login.css", "/login.js",
                                 "/login", "/actuator/health").permitAll()
                .requestMatchers("/admin.html", "/admin.js", "/addons.html",
                                 "/addons.js", "/catalog.json").hasRole("ADMIN")
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
                .deleteCookies("JSESSIONID").invalidateHttpSession(true).clearAuthentication(true))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .addFilterAfter(new SibylFirstLoginFilter(accounts),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
