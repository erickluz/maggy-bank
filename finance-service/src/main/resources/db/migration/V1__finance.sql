CREATE SEQUENCE account_number_seq START WITH 100001;

CREATE TABLE account (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL UNIQUE,
  account_number VARCHAR(20) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  balance_cents BIGINT NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transfer (
  id UUID PRIMARY KEY,
  request_id UUID NOT NULL UNIQUE,
  source_account_id UUID NOT NULL REFERENCES account(id),
  destination_account_id UUID NOT NULL REFERENCES account(id),
  source_account_number VARCHAR(20) NOT NULL,
  source_display_name VARCHAR(120) NOT NULL,
  destination_account_number VARCHAR(20) NOT NULL,
  destination_display_name VARCHAR(120) NOT NULL,
  amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
  completed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE statement_entry (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES account(id),
  transfer_id UUID REFERENCES transfer(id),
  type VARCHAR(20) NOT NULL CHECK (type IN ('OPENING_CREDIT', 'TRANSFER_IN', 'TRANSFER_OUT')),
  amount_cents BIGINT NOT NULL,
  balance_after_cents BIGINT NOT NULL CHECK (balance_after_cents >= 0),
  occurred_at TIMESTAMPTZ NOT NULL,
  description VARCHAR(250) NOT NULL
);
CREATE INDEX statement_account_time_idx ON statement_entry(account_id, occurred_at DESC, id DESC);
