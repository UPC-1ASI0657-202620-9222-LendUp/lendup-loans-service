package com.lendup.prestamos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
  @Bean OpenAPI loansOpenApi() {
    return new OpenAPI()
        .info(new Info().title("LendUp Loans API").version("1.0.0").description("Ciclo del préstamo: entrega, recepción, extensiones, reprogramaciones, devolución y cierre."))
        .components(new Components().addSecuritySchemes("firebaseBearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("firebaseBearer"));
  }
}
