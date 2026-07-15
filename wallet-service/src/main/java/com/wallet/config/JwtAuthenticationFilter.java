package com.wallet.config;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Каждый HTTP-запрос проходит через этот фильтр:
 * 1. Читает Authorization: Bearer <token>.
 * 2. Парсит токен через JwtService.
 * 3. Кладёт Authentication в SecurityContextHolder.
 *
 * Если токена нет или он невалиден — пропускает запрос дальше БЕЗ аутентификации.
 * Дальнейшую защиту обеспечит цепочка authorizeHttpRequests в SecurityConfig
 * (вернёт 401 для защищённых URL).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER);
        if (authHeader == null || !authHeader.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(PREFIX.length());
        UUID userId;
        try {
            userId = jwtService.extractUserId(token);
        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        // Если уже есть аутентификация — не перезаписываем
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId.toString());

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (UsernameNotFoundException e) {
                log.debug("User from token not found: {}", userId);
            }
        }

        chain.doFilter(request, response);
    }
}
