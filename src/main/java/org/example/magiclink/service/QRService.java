package org.example.magiclink.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.magiclink.entity.QRTokenEntity;
import org.example.magiclink.repository.QRTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QRService {

    private final QRTokenRepository qrTokenRepository;

    @Value("${app.qr-login.token-expiry-minutes:5}")
    private int tokenExpiryMinutes;

    @Value("${app.magic-link.base-url:http://localhost:8080}")
    private String baseUrl;

    public QRTokenEntity generateQRToken(String userEmail, String generatingSessionId) {
        QRTokenEntity entity = new QRTokenEntity();
        entity.setToken(UUID.randomUUID().toString());
        entity.setUserEmail(userEmail);
        entity.setGeneratingSessionId(generatingSessionId);
        entity.setScanningSessionId(null);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
        entity.setStatus(QRTokenEntity.QRTokenStatus.PENDING);
        entity.setLoginType(QRTokenEntity.QRLoginType.PHONE_TO_BROWSER);
        entity.setScannedAt(null);
        entity.setDeviceInfo(null);
        entity.setIp(null);

        return qrTokenRepository.save(entity);
    }

    public QRTokenEntity generateBrowserLoginToken(String deviceInfo, String ip, String scanningSessionId) {
        QRTokenEntity entity = new QRTokenEntity();
        entity.setToken(UUID.randomUUID().toString());
        entity.setUserEmail(null);
        entity.setGeneratingSessionId(null);
        entity.setScanningSessionId(scanningSessionId);
        entity.setDeviceInfo(deviceInfo);
        entity.setIp(ip);
        entity.setScannedAt(null);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
        entity.setStatus(QRTokenEntity.QRTokenStatus.PENDING);
        entity.setLoginType(QRTokenEntity.QRLoginType.BROWSER_TO_BROWSER);

        return qrTokenRepository.save(entity);
    }

    public String generateQRCodeImage(String token) throws Exception {
        Optional<QRTokenEntity> tokenEntity = qrTokenRepository.findByToken(token);
        String qrUrl;
        if (tokenEntity.isPresent() &&
                tokenEntity.get().getLoginType() == QRTokenEntity.QRLoginType.BROWSER_TO_BROWSER) {
            qrUrl = baseUrl + "/qr/login/scan?token=" + token;
        } else {
            qrUrl = baseUrl + "/qr/scan?token=" + token;
        }

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrUrl, BarcodeFormat.QR_CODE, 300, 300);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

        byte[] imageBytes = outputStream.toByteArray();
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }

    public Optional<QRTokenEntity> validateToken(String token) {
        Optional<QRTokenEntity> opt = qrTokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        QRTokenEntity entity = opt.get();
        if (hasExpired(entity)) {
            markExpired(entity);
            return Optional.empty();
        }

        return Optional.of(entity);
    }

    public Optional<QRTokenEntity> findToken(String token) {
        return qrTokenRepository.findByToken(token);
    }

    public boolean hasExpired(QRTokenEntity token) {
        return token.getExpiresAt().isBefore(LocalDateTime.now());
    }

    public void markExpired(QRTokenEntity token) {
        if (token.getStatus() != QRTokenEntity.QRTokenStatus.EXPIRED) {
            token.setStatus(QRTokenEntity.QRTokenStatus.EXPIRED);
            qrTokenRepository.save(token);
        }
    }

    public boolean approveToken(String token) {
        Optional<QRTokenEntity> opt = qrTokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return false;
        }

        QRTokenEntity entity = opt.get();
        if (hasExpired(entity)) {
            markExpired(entity);
            return false;
        }

        if (entity.getLoginType() != QRTokenEntity.QRLoginType.PHONE_TO_BROWSER) {
            return false;
        }

        if (entity.getStatus() != QRTokenEntity.QRTokenStatus.PENDING) {
            return false;
        }

        if (entity.getScanningSessionId() == null) {
            return false;
        }

        entity.setStatus(QRTokenEntity.QRTokenStatus.APPROVED);
        qrTokenRepository.save(entity);
        log.info("QR token approved: {}", token);
        return true;
    }

    public boolean denyToken(String token) {
        Optional<QRTokenEntity> opt = qrTokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return false;
        }

        QRTokenEntity entity = opt.get();
        if (hasExpired(entity)) {
            markExpired(entity);
            return false;
        }

        entity.setStatus(QRTokenEntity.QRTokenStatus.DENIED);
        qrTokenRepository.save(entity);
        log.info("QR token denied: {}", token);
        return true;
    }

    public Optional<String> consumeToken(String token, String sessionId) {
        Optional<QRTokenEntity> opt = qrTokenRepository.findByToken(token);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        QRTokenEntity entity = opt.get();
        if (hasExpired(entity)) {
            markExpired(entity);
            return Optional.empty();
        }

        if (!Objects.equals(entity.getScanningSessionId(), sessionId)) {
            return Optional.empty();
        }

        if (entity.getStatus() != QRTokenEntity.QRTokenStatus.APPROVED) {
            return Optional.empty();
        }

        entity.setStatus(QRTokenEntity.QRTokenStatus.CONSUMED);
        qrTokenRepository.save(entity);

        return Optional.ofNullable(entity.getUserEmail());
    }

    public QRTokenEntity.QRTokenStatus getTokenStatus(String token) {
        return qrTokenRepository.findByToken(token)
                .map(QRTokenEntity::getStatus)
                .orElse(QRTokenEntity.QRTokenStatus.EXPIRED);
    }

    public void saveToken(QRTokenEntity token) {
        qrTokenRepository.save(token);
    }
}


