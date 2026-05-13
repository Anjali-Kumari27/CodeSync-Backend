package com.codesync.auth.config;

import com.codesync.auth.dto.AuthResponse;
import com.codesync.auth.model.Role;
import com.codesync.auth.model.User;
import com.codesync.auth.repository.UserRepository;
import com.codesync.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                		User.builder()
                        .username(email.split("@")[0])
                        .email(email)
                        .fullName(name)
                        .password("")
                        .provider("GOOGLE")
                        .role(Role.USER)
                        .enabled(true)
                        .build()
                ));

        String token = jwtService.generateToken(user);

        String redirectUrl =
                "http://localhost:5173/oauth-success?token=" + token;

        response.sendRedirect(redirectUrl);
    }
}