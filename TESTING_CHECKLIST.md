# Testing Checklist for Transaction Payors Update

## Files Modified

1. ✅ `MultiTransactionModels.kt` - Updated data models
2. ✅ `MultiTransactionRepository.kt` - Updated insert and added update logic
3. ✅ `CreateGroupActivity.kt` - Fixed image tint issue
4. ✅ `EditGroupActivity.kt` - Fixed image tint issue
5. ✅ `GroupsActivity.kt` - Fixed image display with tint and caching

## Database Schema Requirements

Ensure your `transaction_payors` table has these columns:
- `id` (int8, primary key)
- `transaction_id` (int8)
- `user_id` (int8)
- `initial_amount_paid` (numeric) - NEW
- `current_amount_paid` (numeric) - NEW
- `excess_amount` (numeric) - NEW
- `transaction_items_id` (int8)
- `created_at` (timestamptz)
- `status` (int2) - NEW

## Testing Scenarios

### 1. Create New Transaction - Single Payor
**Steps:**
1. Open MultiTransactionActivity
2. Select a group with 4 members
3. Add one transaction item (e.g., "Dinner - $100")
4. Select single payor mode
5. Choose one member as payor
6. Submit

**Expected Results:**
- Transaction created successfully
- In `transaction_payors` table:
  - Payor: `initial_amount_paid = 100`, `current_amount_paid = 100`, `excess_amount = 75`, `status = 1`
  - Other 3 members: `initial_amount_paid = 0`, `current_amount_paid = 0`, `excess_amount = 0`, `status = 0`

### 2. Create New Transaction - Multiple Payors
**Steps:**
1. Add transaction item "Groceries - $200"
2. Select multiple payors mode
3. Set Member A pays $150, Member B pays $50
4. Submit (4 members total)

**Expected Results:**
- Split per member = $50
- Member A: `initial_amount_paid = 150`, `current_amount_paid = 150`, `excess_amount = 100`, `status = 1`
- Member B: `initial_amount_paid = 50`, `current_amount_paid = 50`, `excess_amount = 0`, `status = 1`
- Members C & D: `initial_amount_paid = 0`, `current_amount_paid = 0`, `excess_amount = 0`, `status = 0`

### 3. Partial Payment
**Steps:**
1. Create transaction with Member A paying $20 (split = $25)
2. Check database

**Expected Results:**
- Member A: `initial_amount_paid = 20`, `current_amount_paid = 20`, `excess_amount = 0`, `status = 2` (pending)

### 4. Update Payment (Future Feature)
**Steps:**
1. Call `repository.updatePayorPayment(txId, userId, itemId, 25.0, 25.0)`

**Expected Results:**
- `current_amount_paid = 25`
- `excess_amount = 0`
- `status = 1` (settled)
- `initial_amount_paid` remains unchanged

### 5. Group Image Upload
**Steps:**
1. Create new group
2. Upload an image
3. Save group
4. Return to groups list

**Expected Results:**
- Image displays without purple tint
- Image saved to Supabase storage
- URL saved in database
- Image displays in groups list

### 6. Edit Group Image
**Steps:**
1. Edit existing group
2. Change image
3. Save

**Expected Results:**
- New image displays in dialog
- Image updated in storage
- Groups list shows new image

## Common Issues & Solutions

### Issue: Images not displaying
**Solution:** Ensure `group_images` bucket is public in Supabase

### Issue: Purple tint on images
**Solution:** Code now uses `imageTintList = null` instead of `clearColorFilter()`

### Issue: Old images cached
**Solution:** Code now uses `skipMemoryCache(true)` and `diskCacheStrategy(NONE)`

### Issue: Compilation errors
**Check:**
- All imports are correct
- Supabase SDK version is compatible
- Glide library is included in dependencies

## Build Commands

```bash
# Clean build
gradlew clean

# Build debug APK
gradlew assembleDebug

# Install on device
gradlew installDebug

# Run tests
gradlew test
```

## Verification Queries

```sql
-- Check transaction_payors structure
SELECT * FROM transaction_payors LIMIT 1;

-- Verify status distribution
SELECT status, COUNT(*) 
FROM transaction_payors 
GROUP BY status;

-- Check excess amounts
SELECT user_id, initial_amount_paid, current_amount_paid, excess_amount, status
FROM transaction_payors
WHERE excess_amount > 0;

-- Verify all members have records
SELECT t.id, COUNT(tp.id) as payor_count
FROM transactions t
LEFT JOIN transaction_payors tp ON t.id = tp.transaction_id
GROUP BY t.id;
```

## Next Steps

1. ✅ Build the project
2. ✅ Fix any compilation errors
3. ⏳ Test transaction creation
4. ⏳ Test group image upload
5. ⏳ Verify database records
6. ⏳ Test edge cases (0 amount, negative, etc.)
