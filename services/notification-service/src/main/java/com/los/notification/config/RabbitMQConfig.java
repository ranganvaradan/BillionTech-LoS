package com.los.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "los.notification";
    public static final String QUEUE_SMS = "notification.sms";
    public static final String QUEUE_EMAIL = "notification.email";
    public static final String QUEUE_WHATSAPP = "notification.whatsapp";
    public static final String QUEUE_DLQ = "notification.dlq";

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(QUEUE_SMS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(QUEUE_EMAIL)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue whatsappQueue() {
        return QueueBuilder.durable(QUEUE_WHATSAPP)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QUEUE_DLQ)
                .build();
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding smsBinding(Queue smsQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(smsQueue).to(notificationExchange).with("notification.sms.#");
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationExchange).with("notification.email.#");
    }

    @Bean
    public Binding whatsappBinding(Queue whatsappQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(whatsappQueue).to(notificationExchange).with("notification.whatsapp.#");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @jakarta.annotation.PostConstruct
    void logRabbitTopology() {
        log.info("[NOTIFICATION] queue registered name={} durable=true", QUEUE_EMAIL);
        log.info("[NOTIFICATION] queue registered name={} durable=true", QUEUE_SMS);
        log.info("[NOTIFICATION] queue registered name={} durable=true", QUEUE_WHATSAPP);
        log.info("[NOTIFICATION] queue registered name={} durable=true", QUEUE_DLQ);
        log.info("[NOTIFICATION] binding registered exchange={} queue={} keyPattern=notification.email.#",
                EXCHANGE, QUEUE_EMAIL);
        log.info("[NOTIFICATION] consumer active expectedRoutingKeyExample=notification.email.esign_link");
    }
}
