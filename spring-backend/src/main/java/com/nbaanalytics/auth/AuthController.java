package com.nbaanalytics.auth;

import com.nbaanalytics.auth.JwtAuthFilter.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  public record LoginBody(
      @Email @NotBlank String email, @NotBlank @jakarta.validation.constraints.Size(min = 8) String password) {}

  public record RegisterBody(
      @Email @NotBlank String email, @NotBlank @jakarta.validation.constraints.Size(min = 8) String password) {}

  @PostMapping("/register")
  public Map<String, Object> register(
      @Valid @RequestBody RegisterBody body, HttpServletResponse res) {
    return authService.register(body.email(), body.password(), res);
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody LoginBody body, HttpServletResponse res) {
    return authService.login(body.email(), body.password(), res);
  }

  @PostMapping("/refresh")
  public Map<String, Object> refresh(HttpServletRequest req, HttpServletResponse res) {
    return authService.refresh(req, res);
  }

  @PostMapping("/logout")
  public void logout(
      @AuthenticationPrincipal AuthPrincipal user, HttpServletResponse res) {
    authService.logout(user.userId(), res);
  }
}
