package com.lendup.prestamos.infrastructure.scheduler;

import com.lendup.prestamos.application.port.in.LoanEventUseCases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Vencimiento Scheduler: pasa a VENCIDO los préstamos activos que llegaron a su fecha vigente sin devolución (RF53). */
@Component
public class OverdueScheduler {
  private static final Logger log = LoggerFactory.getLogger(OverdueScheduler.class);
  private final LoanEventUseCases loans;
  private final int batchSize;

  public OverdueScheduler(LoanEventUseCases loans, @Value("${lendup.overdue.batch-size:100}") int batchSize) { this.loans = loans; this.batchSize = batchSize; }

  @Scheduled(fixedDelayString = "${lendup.overdue.check-delay-ms:60000}")
  public void markOverdue() {
    int vencidos = loans.marcarVencidos(batchSize);
    if (vencidos > 0) log.info("Préstamos marcados como vencidos: {}", vencidos);
  }
}
