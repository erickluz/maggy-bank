package bank.maggy.identity.infrastructure;

import bank.maggy.identity.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Customer> MAPPER = (rs, row) -> new Customer(
            rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("username"),
            rs.getString("password_hash"), rs.getString("account_status"));

    public CustomerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Customer customer) {
        jdbc.update("INSERT INTO customer(id, display_name, username, password_hash, account_status) VALUES (?, ?, ?, ?, ?)",
                customer.id(), customer.displayName(), customer.username(), customer.passwordHash(), customer.accountStatus());
    }

    public Optional<Customer> findById(UUID id) {
        return jdbc.query("SELECT * FROM customer WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Customer> findByUsername(String username) {
        return jdbc.query("SELECT * FROM customer WHERE username = ?", MAPPER, username).stream().findFirst();
    }

    public List<Customer> findAll() {
        return jdbc.query("SELECT * FROM customer ORDER BY created_at, id", MAPPER);
    }

    public int update(Customer customer) {
        return jdbc.update("UPDATE customer SET display_name = ?, username = ?, password_hash = ? WHERE id = ?",
                customer.displayName(), customer.username(), customer.passwordHash(), customer.id());
    }

    public int delete(UUID id) { return jdbc.update("DELETE FROM customer WHERE id = ?", id); }
}
