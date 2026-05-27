-- ==============================================================================
-- QA VALIDATION: ORPHANED DATA & MEMBERSHIP
-- Purpose: Ensures referential integrity that triggers/app logic might miss.
-- ==============================================================================

-- 1. Find Group Messages sent by users who are NOT members of that group
-- This identifies security/logic flaws in the chat implementation.
SELECT
    gm.id AS message_id,
    gm.group_id,
    gm.user_id AS sender_id,
    u.username
FROM group_messages gm
JOIN users u ON gm.user_id = u.user_id
LEFT JOIN group_members gmb ON gm.group_id = gmb.group_id AND gm.user_id = gmb.user_id
WHERE gmb.user_id IS NULL;


-- 2. Identify Groups with no members (Orphaned Groups)
SELECT
    g.id,
    g.group_name,
    g.created_at
FROM groups g
LEFT JOIN group_members gm ON g.id = gm.group_id
WHERE gm.group_id IS NULL;


-- 3. Check for Payors assigned to transactions who are NOT in the group
SELECT
    tp.transaction_id,
    tp.user_id,
    t.group_id
FROM transaction_payors tp
JOIN transactions t ON tp.transaction_id = t.id
LEFT JOIN group_members gm ON t.group_id = gm.group_id AND tp.user_id = gm.user_id
WHERE gm.user_id IS NULL;
