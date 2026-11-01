package bank.maggy.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @Schema(description = "Nome fictício apresentado ao cliente; espaços nas extremidades são removidos.", example = "Maria Silva") @NotBlank(message = "Informe o nome de exibição.") @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.") String displayName,
        @Schema(description = "Nome único para login; aceita letras, números, ponto, hífen e sublinhado.", example = "maria.silva") @NotBlank(message = "Informe o nome de usuário.") @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}", message = "O usuário deve ter de 3 a 60 caracteres e conter apenas letras, números, ponto, hífen ou sublinhado.") String username,
        @NotBlank(message = "Informe a senha.") @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres.") @Schema(description = "Senha com 8 a 72 caracteres e no máximo 72 bytes UTF-8; armazenada como hash BCrypt e nunca retornada.", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password) { }
