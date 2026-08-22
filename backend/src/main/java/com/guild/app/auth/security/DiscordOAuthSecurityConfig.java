package com.guild.app.auth.security;

import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@Profile("discord")
@Import(OAuth2ClientAutoConfiguration.class)
public class DiscordOAuthSecurityConfig {

    private final DiscordOAuth2SuccessHandler successHandler;
    private final DiscordOAuth2FailureHandler failureHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public DiscordOAuthSecurityConfig(
            DiscordOAuth2SuccessHandler successHandler,
            DiscordOAuth2FailureHandler failureHandler,
            CorsConfigurationSource corsConfigurationSource) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @Order(0)
    SecurityFilterChain discordOAuthFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }
}
