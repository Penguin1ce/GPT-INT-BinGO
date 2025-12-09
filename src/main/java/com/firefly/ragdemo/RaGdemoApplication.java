package com.firefly.ragdemo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.firefly.ragdemo.mapper")
@EnableScheduling
public class RaGdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaGdemoApplication.class, args);
    }

}
