CREATE TABLE customer (
  id UUID PRIMARY KEY,
  display_name VARCHAR(120) NOT NULL,
  username VARCHAR(60) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  account_status VARCHAR(10) NOT NULL CHECK (account_status IN ('PENDING', 'READY')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX customer_pending_idx ON customer(created_at) WHERE account_status = 'PENDING';
