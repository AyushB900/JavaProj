package com.banksync.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfiguration {
    @Bean @Profile("!prod")
    UserDetailsService noLocalUsers() {
        return username -> { throw new UsernameNotFoundException("No local authentication configured"); };
    }
    @Bean @Profile("prod")
    UserDetailsService productionUsers(@Value("${BANKSYNC_API_USER}") String username,
                                      @Value("${BANKSYNC_API_PASSWORD}") String password) {
        if (username.isBlank() || password.length() < 16 || password.length() > 72) {
            throw new IllegalStateException("Production requires an API username and a password of 16–72 characters");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("TRANSFER_SERVICE").build());
    }
    @Bean @Profile("!prod")
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
    @Bean @Profile("prod")
    SecurityFilterChain productionSecurity(HttpSecurity http) throws Exception {
        // Stateless machine-to-machine API. Terminate TLS at the deployment ingress.
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults()).build();
    }
}
