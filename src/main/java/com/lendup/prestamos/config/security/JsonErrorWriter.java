package com.lendup.prestamos.config.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Errores de seguridad con el mismo formato que el resto de la API, en UTF-8. */
final class JsonErrorWriter {
  private JsonErrorWriter() {}

  static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
  }
}
