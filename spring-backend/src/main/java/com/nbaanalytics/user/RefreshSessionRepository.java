package com.nbaanalytics.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

  void deleteByUserId(Integer userId);

  Optional<RefreshSession> findFirstByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
}
