package bank.maggy.identity.api;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record CustomerResponse(
        @Schema(description = "Identificador único do cadastro.") UUID customerId,
        @Schema(description = "Nome de exibição fictício.", example = "Maria Silva") String displayName,
        @Schema(description = "Nome de usuário utilizado no login.", example = "maria.silva") String username,
        @Schema(description = "Estado armazenado para a conta. Novos cadastros retornam READY, sem garantia de abertura no financeiro.", allowableValues = {"PENDING", "READY"}) String accountStatus) { }
