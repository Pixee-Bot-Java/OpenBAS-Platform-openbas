package io.openex.security;

import io.openex.database.model.Token;
import io.openex.database.model.User;
import io.openex.database.repository.TokenRepository;
import io.openex.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "openex_token";
    private static final String TOKEN_NAME = "X-Authorization-Token";
    private TokenRepository tokenRepository;
    private UserService userService;

    @Autowired
    public void setTokenRepository(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    private String getAuthToken(HttpServletRequest request) {
        String header = request.getHeader(TOKEN_NAME);
        Cookie[] cookies = ofNullable(request.getCookies()).orElse(new Cookie[0]);
        Optional<Cookie> defaultCookie = Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName())).findFirst();
        return hasLength(header) ? header :
                defaultCookie.orElseGet(() -> new Cookie(COOKIE_NAME, null)).getValue();
    }

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Extract from request
        String authToken = getAuthToken(request);
        if (authToken != null) {
            Optional<Token> token = tokenRepository.findByValue(authToken);
            SecurityContext userContext = SecurityContextHolder.getContext();
            if (token.isPresent()) {
                User user = token.get().getUser();
                userService.createUserSession(user);
            } else if (userContext.getAuthentication() != null) {
                SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            }
        }
        filterChain.doFilter(request, response);
    }
}