package dev.sachin.incidentlens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
class SecurityConfig {
    @Bean SecurityFilterChain apiSecurity(HttpSecurity http, @Value("${app.mode}") String mode) throws Exception {
        // Stateless machine API; browsers should not store these Basic credentials.
        http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(
            org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(a -> {
            a.requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll();
            if (mode.equals("incident")) a.anyRequest().authenticated(); else a.anyRequest().permitAll();
        }).httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
