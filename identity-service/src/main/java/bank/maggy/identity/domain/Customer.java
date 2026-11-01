package bank.maggy.identity.domain;

import java.util.UUID;

public record Customer(UUID id, String displayName, String username, String passwordHash, String accountStatus) { }
