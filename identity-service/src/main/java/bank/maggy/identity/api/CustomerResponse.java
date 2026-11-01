package bank.maggy.identity.api;

import java.util.UUID;

public record CustomerResponse(UUID customerId, String displayName, String username, String accountStatus) { }
