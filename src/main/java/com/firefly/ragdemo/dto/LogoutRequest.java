package com.firefly.ragdemo.dto;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken; // 可选
}
