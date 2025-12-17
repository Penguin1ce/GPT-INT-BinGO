package com.firefly.ragdemo.service.impl;

import com.firefly.ragdemo.dto.LoginRequest;
import com.firefly.ragdemo.dto.RegisterRequest;
import com.firefly.ragdemo.vo.ApiResponse;
import com.firefly.ragdemo.vo.LoginResponseVO;
import com.firefly.ragdemo.vo.UserVO;
import com.firefly.ragdemo.entity.RefreshToken;
import com.firefly.ragdemo.entity.User;
import com.firefly.ragdemo.security.JwtUtil;
import com.firefly.ragdemo.service.AuthService;
import com.firefly.ragdemo.service.RefreshTokenService;
import com.firefly.ragdemo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public ApiResponse<UserVO> register(RegisterRequest request) {
        if (userService.existsByUsername(request.getUsername())) {
            return ApiResponse.error("用户名已存在", 409);
        }
        if (userService.existsByEmail(request.getEmail())) {
            return ApiResponse.error("邮箱已存在", 409);
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userService.save(user);

        UserVO userVO = UserVO.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .createdAt(savedUser.getCreatedAt())
                .build();

        return ApiResponse.success("注册成功", userVO, 201);
    }

    @Override
    @Transactional
    public ApiResponse<LoginResponseVO> login(LoginRequest request) {
        Optional<User> userOpt = userService.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return ApiResponse.error("用户名或密码错误", 401);
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ApiResponse.error("用户名或密码错误", 401);
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ApiResponse.error("账户已被禁用", 403);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUsername());

        userService.updateLastLogin(user.getId());

        UserVO userVO = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();

        LoginResponseVO responseVO = LoginResponseVO.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .user(userVO)
                .build();

        return ApiResponse.success("登录成功", responseVO);
    }

    @Override
    @Transactional
    public ApiResponse<LoginResponseVO> refreshToken(String refreshTokenValue) {
        if (!refreshTokenService.isValidRefreshToken(refreshTokenValue)) {
            return ApiResponse.error("刷新令牌无效或已过期", 401);
        }
        Optional<RefreshToken> tokenOpt = refreshTokenService.findValidToken(refreshTokenValue);
        if (tokenOpt.isEmpty()) {
            return ApiResponse.error("刷新令牌无效或已过期", 401);
        }
        RefreshToken refreshToken = tokenOpt.get();
        String userId = refreshToken.getUserId();
        Optional<User> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            return ApiResponse.error("用户不存在", 404);
        }
        User user = userOpt.get();

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUsername());
        refreshTokenService.revokeToken(refreshTokenValue);

        LoginResponseVO responseVO = LoginResponseVO.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .build();

        return ApiResponse.success("Token刷新成功", responseVO);
    }

    @Override
    @Transactional
    public ApiResponse<Void> logout(String userId, String refreshTokenValue) {
        refreshTokenService.revokeAllUserTokens(userId);
        if (refreshTokenValue != null && !refreshTokenValue.isEmpty()) {
            refreshTokenService.revokeToken(refreshTokenValue);
        }
        return ApiResponse.success("登出成功", null);
    }
} 
