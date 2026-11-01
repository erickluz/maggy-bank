package bank.maggy.finance.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void rejectsOverdraftAndNonPositiveTransfers() {
        Money balance = new Money(100);
        assertEquals(25, balance.debit(new Money(75)).cents());
        assertThrows(IllegalArgumentException.class, () -> balance.debit(new Money(101)));
        assertThrows(IllegalArgumentException.class, () -> balance.debit(new Money(0)));
        assertThrows(IllegalArgumentException.class, () -> new Money(-1));
    }

    @Test
    void rejectsOverflowBeforePersistingTheCredit() {
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE).credit(new Money(1)));
    }
}
