CREATE ROLE maggy_identity LOGIN PASSWORD 'local-identity-password';
CREATE ROLE maggy_finance LOGIN PASSWORD 'local-finance-password';
CREATE DATABASE maggy_identity OWNER maggy_identity;
CREATE DATABASE maggy_finance OWNER maggy_finance;
