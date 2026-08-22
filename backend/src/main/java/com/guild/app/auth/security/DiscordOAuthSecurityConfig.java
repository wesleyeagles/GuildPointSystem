package com.guild.app.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@Profile("discord")
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
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${spring.security.oauth2.client.registration.discord.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.discord.client-secret}") String clientSecret) {
        var registration = ClientRegistration.withRegistrationId("discord")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("identify", "email")
                .authorizationUri("https://discord.com/api/oauth2/authorize")
                .tokenUri("https://discord.com/api/oauth2/token")
                .userInfoUri("https://discord.com/api/users/@me")
                .userNameAttributeName("id")
                .clientName("Discord")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
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
