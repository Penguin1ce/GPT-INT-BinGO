package com.firefly.ragdemo.config;

import com.firefly.ragdemo.messaging.ChunkSyncMessagingProperties;
import com.firefly.ragdemo.messaging.ChatSessionMessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 * 消息队列用于：
 * 1. 文档分块同步到MySQL
 * 2. 聊天记录异步持久化
 */
@Configuration
@EnableRabbit
@EnableConfigurationProperties({ChunkSyncMessagingProperties.class, ChatSessionMessagingProperties.class})
public class RabbitMQConfig {

    // 文档分块同步队列
    @Bean
    public Queue chunkSyncQueue(ChunkSyncMessagingProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    public DirectExchange chunkSyncExchange(ChunkSyncMessagingProperties properties) {
        return new DirectExchange(properties.getExchange());
    }

    @Bean
    public Binding chunkSyncBinding(Queue chunkSyncQueue,
                                    DirectExchange chunkSyncExchange,
                                    ChunkSyncMessagingProperties properties) {
        return BindingBuilder.bind(chunkSyncQueue)
                .to(chunkSyncExchange)
                .with(properties.getRoutingKey());
    }

    // 聊天记录持久化队列
    @Bean
    public Queue chatSessionQueue(ChatSessionMessagingProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    public DirectExchange chatSessionExchange(ChatSessionMessagingProperties properties) {
        return new DirectExchange(properties.getExchange());
    }

    @Bean
    public Binding chatSessionBinding(Queue chatSessionQueue,
                                      DirectExchange chatSessionExchange,
                                      ChatSessionMessagingProperties properties) {
        return BindingBuilder.bind(chatSessionQueue)
                .to(chatSessionExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
