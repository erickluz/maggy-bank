package bank.maggy.identity;

import bank.maggy.identity.domain.Customer;
import bank.maggy.identity.infrastructure.CustomerRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "IDENTITY_TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "IDENTITY_TEST_DB_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "IDENTITY_TEST_DB_PASSWORD", matches = ".+")
@SpringBootTest(properties = "spring.flyway.enabled=false")
class IdentityPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("IDENTITY_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("IDENTITY_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("IDENTITY_TEST_DB_PASSWORD"));
    }

    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void persistsUpdatesAndDeletesOnlyItsOwnCustomer() {
        UUID id = UUID.randomUUID();
        String username = "it_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Customer original = new Customer(id, "Original", username, "hash-original", "READY");
            customers.insert(original);
            assertEquals(original, customers.findByUsername(username).orElseThrow());
            Customer updated = new Customer(id, "Atualizado", username, "hash-novo", "READY");
            assertEquals(1, customers.update(updated));
            assertEquals(updated, customers.findById(id).orElseThrow());
            assertEquals(1, customers.delete(id));
            assertTrue(customers.findById(id).isEmpty());
        } finally {
            jdbc.update("DELETE FROM customer WHERE id = ? AND username = ?", id, username);
        }
    }

    @Test
    void concurrentTransactionsCannotCreateTheSameUsername() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String username = "it_" + UUID.randomUUID().toString().replace("-", "");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.List.of(first, second).stream().map(id -> executor.submit(() -> {
                try {
                    return new TransactionTemplate(transactions).execute(status -> {
                        ready.countDown();
                        try {
                            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        customers.insert(new Customer(id, "Concorrente", username, "hash", "READY"));
                        return true;
                    });
                } catch (DuplicateKeyException expected) {
                    return false;
                }
            })).toList();
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                assertNotEquals(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM customer WHERE username = ? AND id IN (?, ?)", Integer.class, username, first, second));
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
                jdbc.update("DELETE FROM customer WHERE username = ? AND id IN (?, ?)", username, first, second);
            }
        }
    }
}
