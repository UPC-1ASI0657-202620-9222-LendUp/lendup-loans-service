package com.lendup.prestamos.config;

import com.lendup.prestamos.domain.model.PoliticaDeCosto;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class CoreConfiguration {
  @Bean Clock utcClock() { return Clock.systemUTC(); }

  @Bean PoliticaDeCosto politicaDeCosto() { return PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS; }

  /** Transacción propia por préstamo en el marcado de vencidos. */
  @Bean TransactionOperations newTransaction(PlatformTransactionManager manager) {
    TransactionTemplate template = new TransactionTemplate(manager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }
}
