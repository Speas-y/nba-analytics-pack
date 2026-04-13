package com.nbaanalytics.config;

import com.nbaanalytics.auth.JwtAuthFilter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Spring Security：JWT 无状态会话；/public/** 与 /auth/* 匿名，其余需登录；CORS 放行本地与 Vercel */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final AppProperties appProperties;

  public SecurityConfig(JwtAuthFilter jwtAuthFilter, AppProperties appProperties) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.appProperties = appProperties;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c = new CorsConfiguration();
    // 用 pattern：credentials=true 时仍可对整类来源放行；含 Vercel 预览域名 https://*.vercel.app
    // 仅 setAllowedOrigins 时，漏写 https:// 或与预览 URL 不一致会直接 403 + 浏览器报 CORS
    c.setAllowedOriginPatterns(corsAllowedOriginPatterns());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("*"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }

  /** 本地开发 + 任意 Vercel 子域 + 配置里的显式来源（自动补全 https://） */
  private List<String> corsAllowedOriginPatterns() {
    LinkedHashSet<String> patterns = new LinkedHashSet<>();
    patterns.add("http://localhost:*");
    patterns.add("http://127.0.0.1:*");
    patterns.add("https://*.vercel.app");
    for (String o : appProperties.getCors().allowedOrigins()) {
      if (o == null || o.isBlank()) {
        continue;
      }
      String t = o.trim();
      if (!t.contains("://")) {
        t = "https://" + t;
      }
      patterns.add(t);
    }
    return new ArrayList<>(patterns);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.cors(c -> {})
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/auth/register", "/auth/login", "/auth/refresh")
                    .permitAll()
                    .requestMatchers("/public/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
