package bank.maggy.finance.infrastructure;

import bank.maggy.finance.application.FinanceService.Account;
import bank.maggy.finance.application.FinanceService.Receipt;
import bank.maggy.finance.application.FinanceService.Recipient;
import bank.maggy.finance.application.FinanceService.StatementEntry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Account> ACCOUNT = (rs, row) -> new Account(rs.getObject("id", UUID.class),
            rs.getString("account_number"), rs.getObject("customer_id", UUID.class), rs.getString("display_name"), rs.getLong("balance_cents"), "BRL");
    private static final RowMapper<Receipt> RECEIPT = (rs, row) -> new Receipt(rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class),
            rs.getString("source_account_number"), rs.getString("source_display_name"), rs.getString("destination_account_number"),
            rs.getString("destination_display_name"), rs.getLong("amount_cents"), "BRL", rs.getTimestamp("completed_at").toInstant());

    public FinanceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockCustomer(UUID id) { jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))) AS lock", Integer.class, id); }
    public void lockRequest(UUID id) { jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?::text, 1))) AS lock", Integer.class, id); }
    public Account accountByCustomer(UUID id) { return jdbc.query("SELECT * FROM account WHERE customer_id = ?", ACCOUNT, id).stream().findFirst().orElse(null); }
    public Account accountByNumber(String number) { return jdbc.query("SELECT * FROM account WHERE account_number = ?", ACCOUNT, number).stream().findFirst().orElse(null); }
    public Account lockedAccount(UUID id) { return jdbc.queryForObject("SELECT * FROM account WHERE id = ? FOR UPDATE", ACCOUNT, id); }
    public String nextAccountNumber() { return jdbc.queryForObject("SELECT nextval('account_number_seq')::text", String.class); }
    public void insertAccount(UUID id, UUID customerId, String number, String displayName, long balance) {
        jdbc.update("INSERT INTO account(id, customer_id, account_number, display_name, balance_cents) VALUES (?, ?, ?, ?, ?)", id, customerId, number, displayName, balance);
    }
    public Recipient recipient(String number) {
        return jdbc.query("SELECT account_number, display_name FROM account WHERE account_number = ?",
                (rs, row) -> new Recipient(rs.getString(1), rs.getString(2)), number).stream().findFirst().orElse(null);
    }
    public List<StatementEntry> statement(UUID id) {
        return jdbc.query("SELECT * FROM statement_entry WHERE account_id = ? ORDER BY occurred_at DESC, id DESC",
                (rs, row) -> new StatementEntry(rs.getObject("id", UUID.class), rs.getString("type"), rs.getLong("amount_cents"),
                        rs.getLong("balance_after_cents"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("description")), id);
    }
    public void updateBalance(UUID id, long balance) { jdbc.update("UPDATE account SET balance_cents = ? WHERE id = ?", balance, id); }
    public void insertTransfer(UUID id, UUID requestId, Account source, Account destination, long amount, Instant now) {
        jdbc.update("INSERT INTO transfer(id, request_id, source_account_id, destination_account_id, source_account_number, source_display_name, destination_account_number, destination_display_name, amount_cents, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, requestId, source.accountId(), destination.accountId(), source.accountNumber(), source.displayName(), destination.accountNumber(), destination.displayName(), amount, Timestamp.from(now));
    }
    public void insertEntry(UUID accountId, UUID transferId, String type, long amount, long balance, Instant now, String description) {
        jdbc.update("INSERT INTO statement_entry(id, account_id, transfer_id, type, amount_cents, balance_after_cents, occurred_at, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), accountId, transferId, type, amount, balance, Timestamp.from(now), description);
    }
    public Receipt receipt(UUID requestId) { return jdbc.query("SELECT * FROM transfer WHERE request_id = ?", RECEIPT, requestId).stream().findFirst().orElse(null); }
}
