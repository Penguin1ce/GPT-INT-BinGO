package com.firefly.ragdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 用于文件索引等耗时操作的异步处理
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * RAG文件索引专用线程池
     * - 核心线程数: 2 (处理日常文件上传)
     * - 最大线程数: 5 (高峰期扩容)
     * - 队列容量: 100 (超过100个任务会触发拒绝策略)
     * - 拒绝策略: CallerRunsPolicy (由调用线程执行，防止任务丢失)
     */
    @Bean(name = "ragIndexExecutor")
    public Executor ragIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(2);

        // 最大线程数
        executor.setMaxPoolSize(5);

        // 队列容量
        executor.setQueueCapacity(100);

        // 线程名称前缀
        executor.setThreadNamePrefix("rag-index-");

        // 线程空闲时间(秒)
        executor.setKeepAliveSeconds(60);

        // 拒绝策略: 由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待终止时间(秒)
        executor.setAwaitTerminationSeconds(60);

        // 初始化
        executor.initialize();

        log.info("RAG索引线程池初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
