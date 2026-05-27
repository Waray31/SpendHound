-- ==============================================================================
-- QA VALIDATION: USER BALANCE INTEGRITY
-- Purpose: Ensures the 'user_balance' table matches the sum of transactions.
-- ==============================================================================

-- 1. Check if 'total_receivable' matches the sum of active 'Owed' transactions
SELECT
    u.user_id,
    u.username,
    ub.total_receivable AS balance_table_receivable,
    SUM(b.borrowed_amount) AS actual_sum_from_borrows
FROM users u
JOIN user_balance ub ON u.user_id = ub.user_id
JOIN borrows b ON u.user_id = b.lender_id
WHERE b.status = 0 -- Assuming 0 is 'Unpaid' or 'Active'
GROUP BY u.user_id, u.username, ub.total_receivable
HAVING ub.total_receivable <> SUM(b.borrowed_amount);


-- 2. Validate that Transaction Splits sum up to the Transaction Item total
-- This identifies "Leaking" amounts in expense distribution.
SELECT
    ti.id AS item_id,
    ti.amount AS expected_total,
    SUM(ts.amount) AS split_sum,
    (ti.amount - SUM(ts.amount)) AS discrepancy
FROM transaction_items ti
JOIN transaction_splits ts ON ti.id = ts.transaction_id -- Assuming schema links splits to item or transaction
GROUP BY ti.id, ti.amount
HAVING ti.amount <> SUM(ts.amount);
