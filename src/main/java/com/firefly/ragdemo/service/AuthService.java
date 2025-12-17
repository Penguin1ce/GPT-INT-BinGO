package com.firefly.ragdemo.service;

import com.firefly.ragdemo.dto.LoginRequest;
import com.firefly.ragdemo.dto.RegisterRequest;
import com.firefly.ragdemo.vo.ApiResponse;
import com.firefly.ragdemo.vo.LoginResponseVO;
import com.firefly.ragdemo.vo.UserVO;

public interface AuthService {

    ApiResponse<UserVO> register(RegisterRequest request);

    ApiResponse<LoginResponseVO> login(LoginRequest request);

    ApiResponse<LoginResponseVO> refreshToken(String refreshTokenValue);

    ApiResponse<Void> logout(String userId, String refreshTokenValue);
}
