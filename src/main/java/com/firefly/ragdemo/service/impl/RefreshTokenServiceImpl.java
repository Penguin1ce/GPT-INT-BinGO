package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.entity.RefreshToken;
import com.firefly.ragdemo.mapper.RefreshTokenMapper;
import com.firefly.ragdemo.security.JwtUtil;
import com.firefly.ragdemo.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(String userId, String username) {
        String tokenValue = jwtUtil.generateRefreshToken(userId, username);

        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .isRevoked(false)
                .build();

        refreshTokenMapper.insert(refreshToken);
        return refreshToken;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findValidToken(String token) {
        return refreshTokenMapper.findValidToken(token, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void revokeToken(String token) {
        refreshTokenMapper.revokeToken(token);
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(String userId) {
        refreshTokenMapper.revokeAllTokensByUserId(userId);
    }

    @Override
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenMapper.deleteExpiredTokens(LocalDateTime.now());
        log.info("Cleaned up expired refresh tokens");
    }

    @Override
    public boolean isValidRefreshToken(String token) {
        try {
            return jwtUtil.validateToken(token) && findValidToken(token).isPresent();
        } catch (Exception e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }
} 
