package com.firefly.ragdemo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseVO {

    private String token;
    private String refreshToken;
    private UserVO user;
}
