package com.nbaanalytics.auth;

import com.nbaanalytics.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final AppProperties appProperties;
  private volatile SecretKey key;

  public JwtTokenService(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  private SecretKey key() {
    if (key == null) {
      synchronized (this) {
        if (key == null) {
          String s = appProperties.getJwt().getSecret();
          key = Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
        }
      }
    }
    return key;
  }

  public long accessTtlSeconds() {
    String raw = appProperties.getJwt().getAccessExpires();
    var m = java.util.regex.Pattern.compile("(\\d+)([smhd])").matcher(raw);
    if (!m.matches()) return 900;
    int n = Integer.parseInt(m.group(1));
    return switch (m.group(2)) {
      case "s" -> n;
      case "m" -> n * 60L;
      case "h" -> n * 3600L;
      case "d" -> n * 86400L;
      default -> 900;
    };
  }

  public String signAccess(int userId, String email, int tokenVersion) {
    long ttl = accessTtlSeconds();
    return Jwts.builder()
        .claim("sub", userId)
        .claim("email", email)
        .claim("tv", tokenVersion)
        .expiration(Date.from(Instant.now().plusSeconds(ttl)))
        .signWith(key())
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
  }
}
