package bank.maggy.finance.domain;

/** Simulated BRL, represented without floating point arithmetic. */
public record Money(long cents) {
    public Money {
        if (cents < 0) throw new IllegalArgumentException("O valor deve ser positivo.");
    }

    public Money debit(Money amount) {
        if (amount.cents == 0 || amount.cents > cents) throw new IllegalArgumentException("Saldo insuficiente.");
        return new Money(cents - amount.cents);
    }

    public Money credit(Money amount) {
        if (amount.cents == 0) throw new IllegalArgumentException("O valor deve ser positivo.");
        return new Money(Math.addExact(cents, amount.cents));
    }
}
