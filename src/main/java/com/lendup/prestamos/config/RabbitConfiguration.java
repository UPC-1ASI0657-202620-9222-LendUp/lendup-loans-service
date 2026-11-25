package com.lendup.prestamos.config;

import java.util.stream.Stream;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
  public static final String EVENTS_EXCHANGE = "lendup.domain.events";
  public static final String DLX = "lendup.domain.events.dlx";
  public static final String QUEUE = "loans.events.v1";
  public static final String DLQ = "loans.events.v1.dlq";
  public static final String RESERVATION_CONFIRMED = "reservas.confirmada.v1";
  public static final String CANCELLATION_REQUESTED = "reservas.cancelacion-solicitada.v1";
  public static final String RESERVATION_CANCELLED = "reservas.cancelada.v1";
  public static final String PAYMENT_CONFIRMED = "pagos.pago-confirmado.v1";
  public static final String PAYMENT_REJECTED = "pagos.pago-rechazado.v1";
  public static final String GUARANTEE_CONSTITUTED = "pagos.garantia-constituida.v1";

  @Bean TopicExchange domainExchange() { return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build(); }
  @Bean DirectExchange deadLetterExchange() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }
  @Bean Queue loansEventsQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build(); }
  @Bean Queue loansEventsDlq() { return QueueBuilder.durable(DLQ).build(); }
  @Bean Binding dlqBinding(Queue loansEventsDlq, DirectExchange deadLetterExchange) { return BindingBuilder.bind(loansEventsDlq).to(deadLetterExchange).with(DLQ); }

  @Bean Declarables consumedEventBindings(Queue loansEventsQueue, TopicExchange domainExchange, EventRoutingProperties routes) {
    return new Declarables(Stream.of(RESERVATION_CONFIRMED, CANCELLATION_REQUESTED, RESERVATION_CANCELLED, PAYMENT_CONFIRMED, PAYMENT_REJECTED, GUARANTEE_CONSTITUTED,
            routes.incidentReportedRoutingKey(), routes.incidentResolvedRoutingKey())
        .distinct().map(key -> (Declarable) BindingBuilder.bind(loansEventsQueue).to(domainExchange).with(key)).toList());
  }
}
