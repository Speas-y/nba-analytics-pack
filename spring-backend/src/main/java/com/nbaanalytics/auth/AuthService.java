package com.nbaanalytics.auth;

import com.nbaanalytics.config.AppProperties;
import com.nbaanalytics.user.RefreshSession;
import com.nbaanalytics.user.RefreshSessionRepository;
import com.nbaanalytics.user.User;
import com.nbaanalytics.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

@Service
public class AuthService {

  private static final String REFRESH_COOKIE = "refresh_token";
  private final UserRepository userRepository;
  private final RefreshSessionRepository refreshSessionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final AppProperties appProperties;
  private final Environment environment;
  private final SecureRandom random = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      RefreshSessionRepository refreshSessionRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      AppProperties appProperties,
      Environment environment) {
    this.userRepository = userRepository;
    this.refreshSessionRepository = refreshSessionRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.appProperties = appProperties;
    this.environment = environment;
  }

  private boolean isProdProfile() {
    for (String p : environment.getActiveProfiles()) {
      if ("prod".equalsIgnoreCase(p)) return true;
    }
    return false;
  }

  private String hashRefresh(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ResponseCookie refreshCookie(String rawRefresh) {
    long maxAgeSeconds = appProperties.getRefresh().getDays() * 86400L;
    boolean prod = isProdProfile();
    ResponseCookie.ResponseCookieBuilder b =
        ResponseCookie.from(REFRESH_COOKIE, rawRefresh)
            .httpOnly(true)
            .path("/")
            .maxAge(maxAgeSeconds)
            .sameSite(prod ? "None" : "Lax")
            .secure(prod);
    return b.build();
  }

  private void clearRefreshCookie(HttpServletResponse res) {
    boolean prod = isProdProfile();
    ResponseCookie c =
        ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .path("/")
            .maxAge(0)
            .sameSite(prod ? "None" : "Lax")
            .secure(prod)
            .build();
    res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
  }

  private Map<String, Object> issueTokens(User user, HttpServletResponse res) {
    String access = jwtTokenService.signAccess(user.getId(), user.getEmail(), user.getTokenVersion());
    byte[] buf = new byte[32];
    random.nextBytes(buf);
    String rawRefresh = HexFormat.of().formatHex(buf);
    Instant exp =
        Instant.now().plusSeconds(appProperties.getRefresh().getDays() * 86400L);
    RefreshSession session = new RefreshSession();
    session.setId(java.util.UUID.randomUUID().toString());
    session.setUserId(user.getId());
    session.setTokenHash(hashRefresh(rawRefresh));
    session.setExpiresAt(exp);
    refreshSessionRepository.save(session);
    res.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(rawRefresh).toString());
    return Map.of(
        "access_token",
        access,
        "expires_in",
        jwtTokenService.accessTtlSeconds());
  }

  @Transactional
  public Map<String, Object> register(String email, String password, HttpServletResponse res) {
    String em = email.toLowerCase();
    if (userRepository.findByEmail(em).isPresent()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Email already registered");
    }
    User u = new User();
    u.setEmail(em);
    u.setPasswordHash(passwordEncoder.encode(password));
    u = userRepository.save(u);
    return issueTokens(u, res);
  }

  @Transactional
  public Map<String, Object> login(String email, String password, HttpServletResponse res) {
    User u =
        userRepository
            .findByEmail(email.toLowerCase())
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    if (!passwordEncoder.matches(password, u.getPasswordHash())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    refreshSessionRepository.deleteByUserId(u.getId());
    return issueTokens(u, res);
  }

  @Transactional
  public Map<String, Object> refresh(HttpServletRequest req, HttpServletResponse res) {
    Cookie c = WebUtils.getCookie(req, REFRESH_COOKIE);
    String token = c != null ? c.getValue() : null;
    if (token == null || token.isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing refresh token");
    }
    String hash = hashRefresh(token);
    Optional<RefreshSession> session =
        refreshSessionRepository.findFirstByTokenHashAndExpiresAtAfter(hash, Instant.now());
    if (session.isEmpty()) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }
    RefreshSession s = session.get();
    refreshSessionRepository.deleteById(s.getId());
    User u =
        userRepository
            .findById(s.getUserId())
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    return issueTokens(u, res);
  }

  @Transactional
  public void logout(int userId, HttpServletResponse res) {
    refreshSessionRepository.deleteByUserId(userId);
    User u =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("user"));
    u.setTokenVersion(u.getTokenVersion() + 1);
    userRepository.save(u);
    clearRefreshCookie(res);
  }
}
