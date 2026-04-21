package emk.ai.chat.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
@Table(name = "anonymous_sessions")
public class ChatSession {
    @Id
    @JsonProperty("token")
    private String token;  // UUID
    @JsonProperty("fingerprint")
    private String fingerprint;
    @JsonProperty("ip_address")
    private String ipAddress;       // User IP
    @JsonProperty("messages_used")
    private int messagesUsed;       // Number of used messages
    @JsonProperty("messages_limit")
    private int messagesLimit;      // Max messages allowed
    @JsonProperty("tokens_used")
    private int tokensUsed;         // Used Tokens
    @JsonProperty("tokens_limit")
    private int tokensLimit;        // Budget tokens max (ex: 50 000)
    @JsonProperty("created_at")
    private Instant createdAt;
    @JsonProperty("expires_at")
    private Instant expiresAt;      // Session expiration time
    @JsonProperty("last_used_at")
    private Instant lastUsedAt;
    @JsonProperty("revoked")
    private boolean revoked;        // Manual revocation

    public ChatSession() {
    }

    public ChatSession(String token, String fingerprint, String ipAddress, int messagesUsed, int messagesLimit, int tokensUsed,
                       int tokensLimit, Instant createdAt, Instant expiresAt, Instant lastUsedAt, boolean revoked) {
        this.token = token;
        this.fingerprint = fingerprint;
        this.ipAddress = ipAddress;
        this.messagesUsed = messagesUsed;
        this.messagesLimit = messagesLimit;
        this.tokensUsed = tokensUsed;
        this.tokensLimit = tokensLimit;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
        this.revoked = revoked;
    }

    public String getToken() {
        return token;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getMessagesUsed() {
        return messagesUsed;
    }

    public int getMessagesLimit() {
        return messagesLimit;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public int getTokensLimit() {
        return tokensLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public void setMessagesUsed(int messagesUsed) {
        this.messagesUsed = messagesUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}

