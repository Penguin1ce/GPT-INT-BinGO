package com.firefly.ragdemo.controller;

import com.firefly.ragdemo.dto.LoginRequest;
import com.firefly.ragdemo.dto.LogoutRequest;
import com.firefly.ragdemo.dto.RefreshTokenRequest;
import com.firefly.ragdemo.dto.RegisterRequest;
import com.firefly.ragdemo.vo.ApiResponse;
import com.firefly.ragdemo.vo.LoginResponseVO;
import com.firefly.ragdemo.vo.UserVO;
import com.firefly.ragdemo.entity.User;
import com.firefly.ragdemo.security.CustomUserPrincipal;
import com.firefly.ragdemo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserVO>> register(@Valid @RequestBody RegisterRequest request) {

        try {
            ApiResponse<UserVO> response = authService.register(request);

            if (response.getSuccess()) {
                return ResponseEntity.status(201).body(response);
            } else {
                return ResponseEntity.status(response.getCode()).body(response);
            }

        } catch (Exception e) {
            log.error("用户注册失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("服务器内部错误"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseVO>> login(@Valid @RequestBody LoginRequest request) {

        try {
            ApiResponse<LoginResponseVO> response = authService.login(request);
            return ResponseEntity.status(response.getCode()).body(response);

        } catch (Exception e) {
            log.error("用户登录失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("服务器内部错误"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponseVO>> refresh(@Valid @RequestBody RefreshTokenRequest request) {

        try {
            ApiResponse<LoginResponseVO> response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.status(response.getCode()).body(response);

        } catch (Exception e) {
            log.error("Token刷新失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("服务器内部错误"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody(required = false) LogoutRequest request) {

        try {
            String refreshToken = (request != null) ? request.getRefreshToken() : null;
            ApiResponse<Void> response = authService.logout(principal.getUserId(), refreshToken);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("用户登出失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("服务器内部错误"));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserVO>> getProfile(@AuthenticationPrincipal CustomUserPrincipal principal) {

        try {
            User user = principal.getUser();

            UserVO userVO = UserVO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .createdAt(user.getCreatedAt())
                    .lastLogin(user.getLastLogin())
                    .build();

            ApiResponse<UserVO> response = ApiResponse.success("获取用户信息成功", userVO);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("服务器内部错误"));
        }
    }
}
