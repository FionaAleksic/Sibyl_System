package org.sibyl.core;

import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService users() {
        String name = System.getenv("SIBYL_BOOTSTRAP_ADMIN_USERNAME");
        String hash = System.getenv("SIBYL_BOOTSTRAP_ADMIN_BCRYPT");
        if (name == null || name.isBlank() || hash == null ||
            !hash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalStateException("Bootstrap admin name and BCrypt hash required via environment; refusing insecure startup");
        }
        return new InMemoryUserDetailsManager(
            User.withUsername(name).password("{bcrypt}" + hash).roles("ADMIN").build()
        );
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(registry -> registry
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
        return http.build();
    }
}
