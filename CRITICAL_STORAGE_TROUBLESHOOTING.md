# 🚨 CRITICAL: Profile Image Upload Not Working - Troubleshooting Guide

**Status:** Images still not uploading to Supabase Storage  
**Date:** March 25, 2026  
**Action Required:** IMMEDIATELY follow this guide

---

## 🔍 Diagnostic Steps (DO THESE FIRST!)

### Step 1: Check Supabase Bucket Exists
```
Go to: https://supabase.com/dashboard → Your Project → Storage

MUST SEE: A bucket named "profile_images"

IF NOT THERE:
1. Click "New bucket"
2. Name: profile_images (plural!)
3. Make public
4. Save
```

### Step 2: Check Bucket is PUBLIC
```
Storage → profile_images bucket → Settings (gear icon)

MUST CHECK:
☐ "Make bucket public" is ENABLED
☐ Access level is PUBLIC
```

### Step 3: Set RLS Policies (CRITICAL!)
```
Go to: SQL Editor and run these commands:

-- Policy 1: Allow authenticated users to upload
CREATE POLICY "Allow authenticated uploads"
ON storage.objects
FOR INSERT
TO authenticated
WITH CHECK (
  bucket_id = (SELECT id FROM storage.buckets WHERE name = 'profile_images')
);

-- Policy 2: Allow authenticated users to update (upsert)
CREATE POLICY "Allow authenticated updates"
ON storage.objects
FOR UPDATE
TO authenticated
USING (
  bucket_id = (SELECT id FROM storage.buckets WHERE name = 'profile_images')
)
WITH CHECK (
  bucket_id = (SELECT id FROM storage.buckets WHERE name = 'profile_images')
);

-- Policy 3: Allow anyone to view
CREATE POLICY "Allow public read"
ON storage.objects
FOR SELECT
USING (
  bucket_id = (SELECT id FROM storage.buckets WHERE name = 'profile_images')
);
```

### Step 4: Verify RLS Policies Applied
```
Go to: Authentication → Policies

MUST SEE for storage.objects:
☑ Allow authenticated uploads
☑ Allow authenticated updates  
☑ Allow public read
```

---

## 🧪 Testing the Fix

### Quick Test Steps

**Step 1: Enable Debug Logging**
In Android Studio Logcat:
```
Filter: "StorageDiagnostics OR SignUpActivity OR ProfileFragment"
```

**Step 2: Build and Run App**
```bash
./gradlew clean build
# Or: Build → Run
```

**Step 3: Run Storage Diagnostics**
Add this to MainActivity or where you test:
```kotlin
lifecycleScope.launch {
    val report = StorageDiagnostics.diagnoseStorageIssues(this@MainActivity)
    Log.d("Test", report.getDetailedReport())
}
```

**Step 4: Check Diagnostics Output**
Look in Logcat for:
```
✓ Supabase client is initialized
✓ Storage module is accessible
✓ profile_images bucket reference obtained
✓ User is authenticated
✓ Test upload successful
✓ Public URL generated
```

If any show ✗, that's your problem!

### Step 5: Test Sign-Up with Image
```
1. Sign Up → Create account
2. Step 2 → Select profile image
3. Click "Sign Up"
4. WATCH LOGCAT for detailed upload logs
```

### Step 6: Monitor Logcat Output

**Success Pattern:**
```
D/SignUpActivity: ===== STARTING PROFILE IMAGE UPLOAD =====
D/SignUpActivity: ✓ Step 1 Complete: Image bytes read (245678 bytes)
D/SignUpActivity: ✓ Step 2 Complete: Bucket reference obtained
D/SignUpActivity: ✓ Step 3 Complete: Upload completed for path: 123/123.jpg
D/SignUpActivity: ✓ Step 4 Complete: URL validation passed
D/SignUpActivity: ===== UPLOAD SUCCESS =====
D/SignUpActivity: Final URL: https://xgcitilgtmxtfcxpmfiz.supabase.co/storage/v1/object/public/profile_images/123/123.jpg
```

**Failure Pattern (Examples):**
```
E/SignUpActivity: ❌ FAILED: Cannot open input stream
        ↓
        FIX: Check file read permissions

E/SignUpActivity: ❌ FAILED to get bucket: bucket_not_found
        ↓
        FIX: Bucket doesn't exist or wrong name

E/SignUpActivity: ❌ FAILED to upload: Permission denied
        ↓
        FIX: RLS policies not set correctly

E/SignUpActivity: ❌ FAILED: Public URL is null or empty
        ↓
        FIX: Bucket is not public
```

### Step 7: Verify in Supabase Console
```
Storage → profile_images → Should see folder structure:
  profile_images/
    ├── 1/
    │   └── 1.jpg
    ├── 2/
    │   └── 2.jpg
    └── 3/
        └── 3.jpg
```

### Step 8: Verify in Database
```sql
SELECT user_id, username, profile_image_url 
FROM users 
WHERE profile_image_url NOT LIKE '%placeholder%'
ORDER BY created_at DESC;

-- Should show full HTTPS URLs like:
-- https://xgcitilgtmxtfcxpmfiz.supabase.co/storage/v1/object/public/profile_images/1/1.jpg
```

---

## 🆘 Common Issues & Solutions

### Issue 1: "bucket_not_found"
```
Error: ❌ FAILED to get bucket: bucket_not_found

CAUSE: Bucket doesn't exist

SOLUTION:
1. Go to Supabase → Storage
2. Create new bucket named "profile_images"
3. Make it PUBLIC
4. Click Save
```

### Issue 2: "Permission denied" on upload
```
Error: ❌ FAILED to upload: Permission denied

CAUSE: RLS policies not configured

SOLUTION:
1. Go to SQL Editor
2. Run the RLS policy SQL commands (see Step 4 above)
3. Verify they were created in Authentication → Policies
```

### Issue 3: "Unauthorized" errors
```
Error: ❌ FAILED to upload: Unauthorized

CAUSE: User not authenticated

SOLUTION:
1. Verify user is logged in
2. Check Supabase auth credentials in DeclareDatabase.kt
3. Ensure SUPABASE_URL and SUPABASE_KEY are correct
```

### Issue 4: "Public URL is null"
```
Error: ❌ FAILED: Public URL is null or empty

CAUSE: Bucket is not public

SOLUTION:
1. Go to Storage → profile_images
2. Click Settings (gear icon)
3. Enable "Make bucket public"
4. Save
```

### Issue 5: Image file is not created in storage
```
Symptom: No file appears in Storage → profile_images

CAUSE: Upload is completing but file isn't persisted

SOLUTION:
1. Check network connection
2. Check Supabase project is active
3. Increase timeouts in DeclareDatabase.kt:
   requestTimeoutMillis = 120000L (2 minutes)
   connectTimeoutMillis = 120000L
   socketTimeoutMillis = 120000L
```

---

## 🔧 Advanced Debugging

### Enable Verbose Logging
In DeclareDatabase.kt, add before creating client:
```kotlin
// Add HTTP logging
httpConfig {
    install(HttpLogging) {
        level = HttpLoggingLevel.BODY
    }
}
```

### Check Supabase Logs
```
1. Go to https://supabase.com/dashboard
2. Your Project → Logs
3. Look for storage upload requests
4. Check response status and errors
```

### Test Upload Directly
```kotlin
// Add to MainActivity for testing
private fun testDirectUpload() {
    lifecycleScope.launch {
        try {
            val bucket = DeclareDatabase.profileImagesBucket
            val testData = "test".toByteArray()
            bucket.upload("test/test.txt", testData, upsert = true)
            Log.d("Test", "Upload successful!")
            val url = bucket.publicUrl("test/test.txt")
            Log.d("Test", "Public URL: $url")
        } catch (e: Exception) {
            Log.e("Test", "Upload failed: ${e.message}", e)
        }
    }
}
```

---

## ✅ Verification Checklist

Before deployment, verify ALL of these:

### Supabase Configuration
- [ ] Bucket "profile_images" exists
- [ ] Bucket is PUBLIC (not private)
- [ ] RLS policies are created (3 policies)
- [ ] RLS policies are enabled
- [ ] User table has "profile_image_url" column
- [ ] Column type is string/text (500 char+)

### Code Configuration
- [ ] StorageDiagnostics.kt file exists
- [ ] SignUpActivity has enhanced logging
- [ ] ProfileFragment has enhanced logging
- [ ] DeclareDatabase.kt has correct URL/Key
- [ ] Timeouts are set to 60+ seconds

### Testing
- [ ] Diagnostics report shows all ✓
- [ ] Test upload succeeds
- [ ] File appears in Storage bucket
- [ ] URL is generated correctly
- [ ] Database URL is saved
- [ ] Image displays in app

---

## 📋 Step-by-Step Resolution

### If nothing works, follow this exact sequence:

**1. Delete Bucket & Start Fresh**
```
1. Storage → profile_images → Settings → Delete bucket
2. Create new bucket with exact name: "profile_images"
3. Make PUBLIC
4. Click Save
```

**2. Apply RLS Policies**
```
1. Go to SQL Editor
2. Copy entire RLS policy block from Step 4 above
3. Run it
4. Verify 3 policies created
```

**3. Rebuild App**
```
./gradlew clean
./gradlew build
```

**4. Test Upload**
```
1. Run app
2. Go to Sign-up
3. Get to Step 2
4. Select image
5. Watch Logcat closely
```

**5. Check Each Step**
```
If Step 1 fails (bytes): File permissions issue
If Step 2 fails (bucket): Bucket doesn't exist
If Step 3 fails (upload): RLS policies needed
If Step 4 fails (URL): Bucket not public
```

---

## 🎯 Expected Success Output

```
D/SignUpActivity: ===== STARTING PROFILE IMAGE UPLOAD =====
D/SignUpActivity: User ID: 42
D/SignUpActivity: ✓ Step 1 Complete: Image bytes read (152344 bytes)
D/SignUpActivity: ✓ Step 2 Complete: Bucket reference obtained
D/SignUpActivity: Upload path: 42/42.jpg
D/SignUpActivity: Uploading to Storage with upsert=true...
D/SignUpActivity: ✓ Step 4 Complete: Upload completed for path: 42/42.jpg
D/SignUpActivity: Generating public URL...
D/SignUpActivity: Generated URL: https://xgcitilgtmxtfcxpmfiz.supabase.co/storage/v1/object/public/profile_images/42/42.jpg
D/SignUpActivity: ✓ Step 6 Complete: URL validation passed
D/SignUpActivity: ===== UPLOAD SUCCESS =====
D/SignUpActivity: Final URL: https://xgcitilgtmxtfcxpmfiz.supabase.co/storage/v1/object/public/profile_images/42/42.jpg
D/SignUpActivity: Updating user in Database for user ID: 42
D/SignUpActivity: Setting username: testuser, profileImageUrl: https://...
D/SignUpActivity: Database update completed. Verifying update...
D/SignUpActivity: Verification successful - Username: testuser, ProfileImageUrl: https://...
```

---

## 📞 Still Not Working?

If you've done ALL of the above and it's still failing:

1. **Collect full Logcat output** - Copy everything from when you click Sign-up until error
2. **Screenshot Supabase Settings** - Show bucket and RLS policies
3. **Check Response Codes** - Look for HTTP 400, 401, 403, 500 errors
4. **Verify Credentials** - Double-check SUPABASE_URL and SUPABASE_KEY

---

**Critical Note:** This is likely a Supabase configuration issue (missing bucket, RLS policies, or public access), NOT a code issue. The enhanced logging will now show you exactly where it fails.

**Last Updated:** March 25, 2026
