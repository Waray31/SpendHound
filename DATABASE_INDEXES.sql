-- PERFORMANCE INDEXES FOR SPENDHOUND
-- Run these in your Supabase SQL Editor (Dashboard → SQL Editor)

-- transactions
CREATE INDEX IF NOT EXISTS idx_transactions_group_id    ON transactions(group_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by  ON transactions(created_by);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at  ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_group_date  ON transactions(group_id, created_at DESC);

-- group_messages
CREATE INDEX IF NOT EXISTS idx_group_messages_group_id  ON group_messages(group_id);
CREATE INDEX IF NOT EXISTS idx_group_messages_user_id   ON group_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_group_messages_created_at ON group_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_group_messages_group_date ON group_messages(group_id, created_at DESC);

-- transaction_splits
CREATE INDEX IF NOT EXISTS idx_tx_splits_transaction_id ON transaction_splits(transaction_id);
CREATE INDEX IF NOT EXISTS idx_tx_splits_user_id        ON transaction_splits(user_id);

-- group_members
CREATE INDEX IF NOT EXISTS idx_group_members_group_id   ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id    ON group_members(user_id);
