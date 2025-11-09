package com.firefly.ragdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.classify.Classifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * 重试机制配置
 * 用于OpenAI API调用等外部服务的容错处理
 */
@Configuration
@EnableRetry
@Slf4j
public class RetryConfig {

    /**
     * OpenAI API调用的重试策略
     * - 网络错误/超时: 重试3次
     * - 5xx服务器错误: 重试3次
     * - 429限流错误: 重试3次
     * - 4xx客户端错误(除429外): 不重试
     */
    @Bean
    public RetryPolicy openaiRetryPolicy() {
        // 可重试的异常及其最大重试次数
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(ResourceAccessException.class, true);  // 网络异常
        retryableExceptions.put(SocketTimeoutException.class, true);   // 超时
        retryableExceptions.put(HttpServerErrorException.class, true); // 5xx错误

        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(3, retryableExceptions);

        // 对于429错误，使用自定义分类器
        ExceptionClassifierRetryPolicy classifierPolicy = new ExceptionClassifierRetryPolicy();
        classifierPolicy.setExceptionClassifier((Classifier<Throwable, RetryPolicy>) throwable -> {
            // 429限流错误重试
            if (throwable instanceof HttpClientErrorException.TooManyRequests) {
                log.warn("检测到OpenAI API限流(429)，将重试");
                return simpleRetryPolicy;
            }
            // 其他4xx错误不重试
            if (throwable instanceof HttpClientErrorException) {
                log.error("OpenAI API客户端错误(4xx)，不重试: {}", throwable.getMessage());
                return new NeverRetryPolicy();
            }
            // 其他异常使用简单策略
            return simpleRetryPolicy;
        });

        return classifierPolicy;
    }

    /**
     * 指数退避策略
     * - 初始延迟: 1秒
     * - 最大延迟: 10秒
     * - 倍数: 2 (每次重试延迟翻倍)
     */
    @Bean
    public ExponentialBackOffPolicy openaiBackOffPolicy() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);  // 1秒
        backOffPolicy.setMaxInterval(10000);     // 10秒
        backOffPolicy.setMultiplier(2.0);        // 指数倍数
        return backOffPolicy;
    }
}
