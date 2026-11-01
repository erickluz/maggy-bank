package bank.maggy.identity.api;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(@Schema(description = "Data e hora em que o erro foi produzido.") Instant timestamp,
                       @Schema(description = "Código HTTP da resposta.", example = "400") int status,
                       @Schema(description = "Mensagem geral que explica o erro.") String message,
                       @Schema(description = "Caminho HTTP da requisição.", example = "/api/identity/register") String path,
                       @Schema(description = "Detalhes por campo em erros de validação; lista vazia nos demais casos.") List<FieldError> errors) {
    public record FieldError(@Schema(description = "Nome do campo inválido.", example = "username") String field,
                             @Schema(description = "Motivo da invalidez do campo.") String message) { }
}
