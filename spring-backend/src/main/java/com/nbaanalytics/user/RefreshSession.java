package com.nbaanalytics.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "RefreshSession")
public class RefreshSession {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false, name = "userId")
  private Integer userId;

  @Column(nullable = false, name = "tokenHash")
  private String tokenHash;

  @Column(nullable = false, name = "expiresAt")
  private Instant expiresAt;

  @Column(nullable = false, name = "createdAt")
  private Instant createdAt = Instant.now();

  @ManyToOne
  @JoinColumn(name = "userId", insertable = false, updatable = false)
  private User user;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }
}
