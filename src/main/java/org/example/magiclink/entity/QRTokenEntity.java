package org.example.magiclink.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "qr_tokens")
@Data
public class QRTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @Column
    private String userEmail;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QRTokenStatus status = QRTokenStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QRLoginType loginType = QRLoginType.PHONE_TO_BROWSER;

    private String deviceInfo;
    private String ip;
    private LocalDateTime scannedAt;
    private String generatingSessionId;
    private String scanningSessionId; // Session that should be authenticated

    public enum QRTokenStatus {
        PENDING,    // Waiting for approval
        APPROVED,   // User approved
        DENIED,     // User denied
        CONSUMED,   // Already used
        EXPIRED     // Time expired
    }

    public enum QRLoginType {
        PHONE_TO_BROWSER,  // Original: PC generates, phone scans to login on phone
        BROWSER_TO_BROWSER // Browser generates, phone scans to login on browser
    }
}
