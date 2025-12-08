package com.firefly.ragdemo.config;

import com.firefly.ragdemo.messaging.ChatMessagingProperties;
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

@Configuration
@EnableRabbit
@EnableConfigurationProperties({ChatMessagingProperties.class, ChunkSyncMessagingProperties.class, ChatSessionMessagingProperties.class})
public class RabbitMQConfig {

    @Bean
    public Queue aiChatQueue(ChatMessagingProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    public DirectExchange aiChatExchange(ChatMessagingProperties properties) {
        return new DirectExchange(properties.getExchange());
    }

    @Bean
    public Binding aiChatBinding(Queue aiChatQueue,
                                 DirectExchange aiChatExchange,
                                 ChatMessagingProperties properties) {
        return BindingBuilder.bind(aiChatQueue)
                .to(aiChatExchange)
                .with(properties.getRoutingKey());
    }

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
                                         MessageConverter messageConverter,
                                         ChatMessagingProperties properties) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setExchange(properties.getExchange());
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
