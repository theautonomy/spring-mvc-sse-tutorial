package com.example.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every page and every SSE stream needs a login, and some need the ADMIN role. EventSource sends the session cookie
 * like any other same-origin request, so streams are protected by the same rules as the pages. POSTs from fetch send
 * the CSRF token as a header (see csrfHeaders() in layout.html).
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot renders error pages (templates/error/403.html) at /error. Let everyone reach
                        // it, or showing "access denied" could itself be denied.
                        .requestMatchers("/error").permitAll()
                        // Admin only: the system dashboard and production deploys. "/**" covers the page, its SSE
                        // stream and its POSTs. Protecting only the page would leave the stream open to anyone
                        // who knows its URL.
                        .requestMatchers("/step4/**", "/step6/**").hasRole("ADMIN")
                        // Everything else: any logged-in user
                        .anyRequest().hasRole("USER"))
                .formLogin(Customizer.withDefaults())   // Spring Security's built-in /login page
                .httpBasic(Customizer.withDefaults())   // lets curl log in with -u alice:password
                .logout(Customizer.withDefaults());     // POST /logout
        return http.build();
    }

    /** Demo users. Two users so the chat in step 5 has someone to talk to. Not for production: plain-text passwords. */
    @Bean
    UserDetailsService users() {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password("{noop}password").roles("USER").build(),
                User.withUsername("bob").password("{noop}password").roles("USER").build(),
                User.withUsername("admin").password("{noop}password").roles("USER", "ADMIN").build());
    }
}
