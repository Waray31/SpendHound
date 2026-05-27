/*
 * SPENDHOUND DATABASE INDEXES
 * ===========================
 *
 * This file contains performance-tuning indexes for the SpendHound Supabase database.
 * These indexes are designed to optimize common query patterns such as:
 * - Filtering transactions by group and date.
 * - Retrieving message history in groups and direct messages.
 * - Looking up users by authentication ID or email.
 * - Accelerating joins between transactions, items, payors, and splits.
 *
 * USAGE:
 * Copy and paste these commands into the Supabase SQL Editor (Dashboard → SQL Editor).
 */

-- ==========================================
-- USERS
-- ==========================================
-- Optimizes user lookups during authentication and invitations.
CREATE INDEX IF NOT EXISTS idx_users_auth_id        ON users(auth_id);
CREATE INDEX IF NOT EXISTS idx_users_email          ON users(email);

-- ==========================================
-- TRANSACTIONS
-- ==========================================
-- Enhances performance for group expense feeds and chronological sorting.
CREATE INDEX IF NOT EXISTS idx_transactions_group_id    ON transactions(group_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_by  ON transactions(created_by);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at  ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_group_date  ON transactions(group_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_status      ON transactions(status);

-- ==========================================
-- TRANSACTION DETAILS (Items, Payors, Splits)
-- ==========================================
-- Improves join performance when fetching line items or debt distributions.
CREATE INDEX IF NOT EXISTS idx_tx_items_transaction_id ON transaction_items(transaction_id);
CREATE INDEX IF NOT EXISTS idx_tx_payors_transaction_id ON transaction_payors(transaction_id);
CREATE INDEX IF NOT EXISTS idx_tx_payors_user_id        ON transaction_payors(user_id);
CREATE INDEX IF NOT EXISTS idx_tx_splits_transaction_id ON transaction_splits(transaction_id);
CREATE INDEX IF NOT EXISTS idx_tx_splits_user_id        ON transaction_splits(user_id);

-- ==========================================
-- GROUPS & MEMBERSHIP
-- ==========================================
-- Speeds up group discovery and member list retrieval.
CREATE INDEX IF NOT EXISTS idx_groups_created_by        ON groups(createdby_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id   ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id    ON group_members(user_id);

-- ==========================================
-- GROUP MESSAGES (Chat)
-- ==========================================
-- Optimizes message retrieval for group chats, especially for loading recent history.
CREATE INDEX IF NOT EXISTS idx_group_messages_group_id  ON group_messages(group_id);
CREATE INDEX IF NOT EXISTS idx_group_messages_user_id   ON group_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_group_messages_created_at ON group_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_group_messages_group_date ON group_messages(group_id, created_at DESC);

-- ==========================================
-- BORROWS / DEBTS
-- ==========================================
-- Improves lookup for borrower/lender relationships and debt status tracking.
CREATE INDEX IF NOT EXISTS idx_borrows_borrower_id      ON borrows(borrower_id);
CREATE INDEX IF NOT EXISTS idx_borrows_lender_id        ON borrows(lender_id);
CREATE INDEX IF NOT EXISTS idx_borrows_created_at       ON borrows(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_borrows_status           ON borrows(status);

-- ==========================================
-- USER BALANCE
-- ==========================================
-- Quick access to aggregated user balances for dashboard views.
CREATE INDEX IF NOT EXISTS idx_user_balance_user_id     ON user_balance(user_id);

-- ==========================================
-- DIRECT MESSAGING
-- ==========================================
-- Enhances 1-on-1 chat performance between users.
CREATE INDEX IF NOT EXISTS idx_direct_messages_sender_id    ON direct_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_direct_messages_recipient_id ON direct_messages(recipient_id);
CREATE INDEX IF NOT EXISTS idx_direct_messages_sent_at      ON direct_messages(sent_at DESC);
