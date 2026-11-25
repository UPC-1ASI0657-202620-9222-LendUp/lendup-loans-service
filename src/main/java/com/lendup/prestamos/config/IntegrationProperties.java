package com.lendup.prestamos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lendup.integrations")
public record IntegrationProperties(Identity identity, Evidence evidence) {
  public IntegrationProperties {
    if (identity == null) identity = new Identity("", "", "", 2000, 3000);
    if (evidence == null) evidence = new Evidence("", "", 1000, 2000);
  }

  public record Identity(String baseUrl, String serviceToken, String firebaseUserUriTemplate, int connectTimeoutMs, int readTimeoutMs) {}
  public record Evidence(String baseUrl, String loanEvidenceUriTemplate, int connectTimeoutMs, int readTimeoutMs) {}
}
