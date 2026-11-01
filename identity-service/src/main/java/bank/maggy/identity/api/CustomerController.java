package bank.maggy.identity.api;

import bank.maggy.identity.application.CustomerService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity/customers")
@Tag(name = "Clientes", description = "CRUD de cadastros. As rotas ainda não exigem autenticação; uso restrito a estudo local.")
public class CustomerController {
    private final CustomerService customers;
    public CustomerController(CustomerService customers) { this.customers = customers; }

    @GetMapping
    @Operation(summary = "Lista clientes", description = "Retorna todos os cadastros, ordenados por data de criação e ID. Não retorna senhas nem hashes. Não há paginação.")
    @ApiResponse(responseCode = "200", description = "Lista de clientes, possivelmente vazia.")
    public List<CustomerResponse> list() { return customers.list(); }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta cliente por ID", description = "Retorna os dados cadastrais e o estado de conta armazenado, sem senha.")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado.")
    @ApiResponse(responseCode = "400", description = "O ID não é um UUID válido.")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado.")
    public CustomerResponse get(@Parameter(description = "UUID do cadastro de cliente.", example = "b3e3eac1-ec7b-4d7c-bc41-1acfbab05b16") @PathVariable UUID id) { return customers.get(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza cliente", description = "Substitui nome de exibição, usuário e senha; os três campos são obrigatórios. O status de conta é preservado.")
    @ApiResponse(responseCode = "200", description = "Cadastro atualizado.")
    @ApiResponse(responseCode = "400", description = "ID ou dados inválidos.")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado.")
    @ApiResponse(responseCode = "409", description = "Nome de usuário já utilizado.")
    public CustomerResponse update(@Parameter(description = "UUID do cadastro a atualizar.") @PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui cliente", description = "Remove apenas o cadastro da identidade. Não exclui nem altera uma eventual conta no financeiro.")
    @ApiResponse(responseCode = "204", description = "Cadastro excluído; sem corpo de resposta.")
    @ApiResponse(responseCode = "400", description = "O ID não é um UUID válido.")
    @ApiResponse(responseCode = "404", description = "Cliente não encontrado.")
    public void delete(@Parameter(description = "UUID do cadastro a excluir.") @PathVariable UUID id) { customers.delete(id); }
}
