package org.example.magiclink.repository;

import org.example.magiclink.entity.OneTimeTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<OneTimeTokenEntity, Long> {
    Optional<OneTimeTokenEntity> findByToken(String token);
}
