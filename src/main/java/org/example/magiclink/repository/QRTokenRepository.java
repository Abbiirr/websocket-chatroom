package org.example.magiclink.repository;

import org.example.magiclink.entity.QRTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QRTokenRepository extends JpaRepository<QRTokenEntity, Long> {
    Optional<QRTokenEntity> findByToken(String token);
}