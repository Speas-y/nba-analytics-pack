package com.nbaanalytics.auth;

import com.nbaanalytics.user.User;
import com.nbaanalytics.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtTokenService jwtTokenService;
  private final UserRepository userRepository;

  public JwtAuthFilter(JwtTokenService jwtTokenService, UserRepository userRepository) {
    this.jwtTokenService = jwtTokenService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String h = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (h == null || !h.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }
    String raw = h.substring(7).trim();
    if (raw.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      Claims claims = jwtTokenService.parse(raw);
      Integer userId = claims.get("sub", Integer.class);
      Integer tv = claims.get("tv", Integer.class);
      if (userId == null || tv == null) {
        filterChain.doFilter(request, response);
        return;
      }
      var ou = userRepository.findById(userId);
      if (ou.isEmpty()) {
        filterChain.doFilter(request, response);
        return;
      }
      User user = ou.get();
      if (user.getTokenVersion() != tv) {
        filterChain.doFilter(request, response);
        return;
      }
      var auth =
          new UsernamePasswordAuthenticationToken(
              new AuthPrincipal(userId, user.getEmail()),
              null,
              List.of(new SimpleGrantedAuthority("ROLE_USER")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch (Exception ignored) {
      SecurityContextHolder.clearContext();
    }
    filterChain.doFilter(request, response);
  }

  public record AuthPrincipal(int userId, String email) {}
}
