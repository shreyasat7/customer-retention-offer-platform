package com.crop.retention.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication and role-based authorisation.
 *
 * <p>The MVP uses an in-memory user store (constraint C1 — no LDAP/SSO in scope). Three
 * accounts map to the three personas; their roles drive both URL authorisation here and
 * the buttons rendered on each page.</p>
 *
 * <p><strong>Demo credentials are intentionally well known.</strong> This instance holds
 * only synthetic seed data (constraint C5) and is never exposed outside localhost.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        UserDetails agent = User.withUsername("agent")
                .password(encoder.encode("agent123"))
                .roles("AGENT")
                .build();

        UserDetails manager = User.withUsername("manager")
                .password(encoder.encode("manager123"))
                .roles("MANAGER")
                .build();

        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "AGENT", "MANAGER")
                .build();

        return new InMemoryUserDetailsManager(agent, manager, admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // the health endpoint is polled by the Jenkins deploy stage and by Ansible
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/login", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?loggedOut")
                .invalidateHttpSession(true)
                .permitAll()
            )
            // H2 console and the Selenium suite post forms; CSRF stays on for the app,
            // Spring's Thymeleaf integration adds the token to every form automatically.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/**"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }
}
