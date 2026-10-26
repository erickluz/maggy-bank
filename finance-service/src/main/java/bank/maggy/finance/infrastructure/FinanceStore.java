package bank.maggy.finance.infrastructure;

import bank.maggy.finance.FinanceService.Account;
import bank.maggy.finance.FinanceService.Receipt;
import bank.maggy.finance.FinanceService.Recipient;
import bank.maggy.finance.FinanceService.StatementEntry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceStore {
    private final JdbcTemplate jdbc;

    public FinanceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<Account> ACCOUNT = (rs, n) -> new Account(
            rs.getObject("id", UUID.class), rs.getString("account_number"), rs.getObject("customer_id", UUID.class),
            rs.getString("display_name"), rs.getLong("balance_cents"), "BRL");
    private static final RowMapper<Receipt> RECEIPT = (rs, n) -> new Receipt(
            rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class),
            rs.getString("source_account_number"), rs.getString("source_display_name"),
            rs.getString("destination_account_number"), rs.getString("destination_display_name"),
            rs.getLong("amount_cents"), "BRL", rs.getTimestamp("completed_at").toInstant());

    public void lockCustomer(UUID customerId) {
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) AS lock", Integer.class, customerId);
    }

    public void lockRequest(UUID requestId) {
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 1))) AS lock", Integer.class, requestId);
    }

    public Account accountByCustomer(UUID customerId) {
        try { return jdbc.queryForObject("SELECT * FROM account WHERE customer_id = ?", ACCOUNT, customerId); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    public Account accountByNumber(String number) {
        try { return jdbc.queryForObject("SELECT * FROM account WHERE account_number = ?", ACCOUNT, number); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    public Account lockedAccount(UUID id) { return jdbc.queryForObject("SELECT * FROM account WHERE id = ? FOR UPDATE", ACCOUNT, id); }
    public String nextAccountNumber() { return jdbc.queryForObject("SELECT nextval('account_number_seq')::text", String.class); }

    public void insertAccount(UUID id, UUID customerId, String number, String displayName) {
        jdbc.update("INSERT INTO account(id, customer_id, account_number, display_name, balance_cents) VALUES (?, ?, ?, ?, 100000)", id, customerId, number, displayName);
    }

    public void insertOpening(UUID accountId) {
        jdbc.update("INSERT INTO statement_entry(id, account_id, type, amount_cents, balance_after_cents, occurred_at, description) VALUES (?, ?, 'OPENING_CREDIT', 100000, 100000, ?, 'Crédito inicial simulado')",
                UUID.randomUUID(), accountId, Timestamp.from(Instant.now()));
    }

    public Recipient recipient(String number) {
        try { return jdbc.queryForObject("SELECT account_number, display_name FROM account WHERE account_number = ?",
                (rs, n) -> new Recipient(rs.getString(1), rs.getString(2)), number); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }

    public List<StatementEntry> statement(UUID accountId) {
        return jdbc.query("SELECT * FROM statement_entry WHERE account_id = ? ORDER BY occurred_at DESC, id DESC",
                (rs, n) -> new StatementEntry(rs.getObject("id", UUID.class), rs.getString("type"), rs.getLong("amount_cents"),
                        rs.getLong("balance_after_cents"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("description")), accountId);
    }

    public void updateBalance(UUID accountId, long balance) {
        jdbc.update("UPDATE account SET balance_cents = ? WHERE id = ?", balance, accountId);
    }

    public void insertTransfer(UUID id, UUID requestId, Account source, Account destination, long amount, Instant now) {
        jdbc.update("INSERT INTO transfer(id, request_id, source_account_id, destination_account_id, source_account_number, source_display_name, destination_account_number, destination_display_name, amount_cents, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, requestId, source.accountId(), destination.accountId(), source.accountNumber(), source.displayName(),
                destination.accountNumber(), destination.displayName(), amount, Timestamp.from(now));
    }

    public void insertEntry(UUID accountId, UUID transferId, String type, long amount, long balance, Instant now, String description) {
        jdbc.update("INSERT INTO statement_entry(id, account_id, transfer_id, type, amount_cents, balance_after_cents, occurred_at, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), accountId, transferId, type, amount, balance, Timestamp.from(now), description);
    }

    public Receipt receipt(UUID requestId) {
        try { return jdbc.queryForObject("SELECT * FROM transfer WHERE request_id = ?", RECEIPT, requestId); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }
}
