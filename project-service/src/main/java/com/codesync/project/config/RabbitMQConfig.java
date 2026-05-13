package com.codesync.project.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

	public static final String INVITE_QUEUE = "invite_queue";
	public static final String INVITE_EXCHANGE = "invite_exchange";
	public static final String INVITE_ROUTING_KEY = "invite_routing_key";
	
	public static final String NOTIFICATION_QUEUE = "notification_queue";
	public static final String NOTIFICATION_EXCHANGE = "notification_exchange";
	public static final String NOTIFICATION_ROUTING_KEY = "notification_routing_key";

	@Bean
	public Queue inviteQueue() {
		return new Queue(INVITE_QUEUE, true);
	}

	@Bean
	public DirectExchange inviteExchange() {
		return new DirectExchange(INVITE_EXCHANGE);
	}

	@Bean
	public Binding inviteBinding(Queue inviteQueue, DirectExchange inviteExchange) {
		return BindingBuilder.bind(inviteQueue).to(inviteExchange).with(INVITE_ROUTING_KEY);
	}

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}
	
	@Bean
	public Queue notificationQueue() {
	    return new Queue(NOTIFICATION_QUEUE, true);
	}

	@Bean
	public DirectExchange notificationExchange() {
	    return new DirectExchange(NOTIFICATION_EXCHANGE);
	}

	@Bean
	public Binding notificationBinding() {
	    return BindingBuilder.bind(notificationQueue())
	            .to(notificationExchange())
	            .with(NOTIFICATION_ROUTING_KEY);
	}
}