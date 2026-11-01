package bank.maggy.finance.domain;

/** Valor monetário em centavos. */
public record Money(long cents) {
    public Money debit(Money amount) {
        throw new UnsupportedOperationException("TODO: implementar débito");
    }

    public Money credit(Money amount) {
        throw new UnsupportedOperationException("TODO: implementar crédito");
    }
}
