package com.guild.app.auth.security;

import com.guild.app.auth.service.AuthService;
import com.guild.app.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class DiscordOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${guild.oauth.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        var oauth2User = (OAuth2User) authentication.getPrincipal();
        var attributes = oauth2User.getAttributes();

        var discordId = String.valueOf(attributes.get("id"));
        var username = (String) attributes.get("username");
        var globalName = (String) attributes.get("global_name");
        var avatarHash = (String) attributes.get("avatar");
        var email = (String) attributes.get("email");

        var nickname = globalName != null && !globalName.isBlank() ? globalName : username;
        var avatarUrl = buildAvatarUrl(discordId, avatarHash);

        try {
            var authResponse = authService.loginWithDiscord(discordId, nickname, avatarUrl, email);
            var redirectUrl = UriComponentsBuilder
                    .fromUriString(frontendUrl + "/auth/callback")
                    .queryParam("token", authResponse.token())
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } catch (AppException ex) {
            var redirectUrl = UriComponentsBuilder
                    .fromUriString(frontendUrl + "/login")
                    .queryParam("error", "discord")
                    .queryParam("message", ex.getMessage())
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        }
    }

    private String buildAvatarUrl(String discordId, String avatarHash) {
        if (avatarHash != null && !avatarHash.isBlank()) {
            return "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png";
        }
        var index = Long.parseLong(discordId) % 5;
        return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
    }
}
