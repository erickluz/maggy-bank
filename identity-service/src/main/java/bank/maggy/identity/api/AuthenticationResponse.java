package bank.maggy.identity.api;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthenticationResponse(@Schema(description = "UUID do cliente autenticado; não é um token de acesso.") UUID customerId) { }
