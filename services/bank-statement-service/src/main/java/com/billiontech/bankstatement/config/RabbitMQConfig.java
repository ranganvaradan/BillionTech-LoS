package com.billiontech.bankstatement.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "bank-statement-exchange";
    public static final String QUEUE_PARSED = "bank-statement.parsed";
    public static final String QUEUE_ANALYSIS_COMPLETE = "bank-statement.analysis-complete";
    public static final String QUEUE_RED_FLAG = "bank-statement.red-flag-detected";
    public static final String ROUTING_PARSED = "bank-statement.parsed";
    public static final String ROUTING_ANALYSIS_COMPLETE = "bank-statement.analysis-complete";
    public static final String ROUTING_RED_FLAG = "bank-statement.red-flag-detected";

    @Bean
    public TopicExchange bankStatementExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue parsedQueue() {
        return QueueBuilder.durable(QUEUE_PARSED).build();
    }

    @Bean
    public Queue analysisCompleteQueue() {
        return QueueBuilder.durable(QUEUE_ANALYSIS_COMPLETE).build();
    }

    @Bean
    public Queue redFlagQueue() {
        return QueueBuilder.durable(QUEUE_RED_FLAG).build();
    }

    @Bean
    public Binding parsedBinding() {
        return BindingBuilder.bind(parsedQueue()).to(bankStatementExchange()).with(ROUTING_PARSED);
    }

    @Bean
    public Binding analysisBinding() {
        return BindingBuilder.bind(analysisCompleteQueue()).to(bankStatementExchange()).with(ROUTING_ANALYSIS_COMPLETE);
    }

    @Bean
    public Binding redFlagBinding() {
        return BindingBuilder.bind(redFlagQueue()).to(bankStatementExchange()).with(ROUTING_RED_FLAG);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
