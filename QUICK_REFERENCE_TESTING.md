# Quick Reference - Profile Image Upload Testing

## 🚀 Quick Start Testing (5 minutes)

### 1. Build & Run
```bash
# In Android Studio
Build → Run app
# Or: Shift+F10
```

### 2. Test Sign-Up with Image
```
1. Click "Sign Up"
2. Fill email: test@example.com
3. Fill password: Test@123456
4. Click "Next"
5. Check email or wait for instant session
6. Enter username: testuser
7. Click "Add Profile Image"
8. Select any image from device
9. Click "Sign Up"
```

### 3. Monitor Logs
```
In Android Studio → Logcat:
- Filter: "SignUpActivity" or "ProfileFragment"
- Watch for log messages with:
  - "Attempting to upload"
  - "Upload completed successfully"
  - "Public URL generated"
  - "Verification successful"
```

### 4. Verify in Supabase
```
Visit: https://supabase.com/dashboard
Project → Storage → profile_images bucket

Look for folder structure: {user_id}/{user_id}.jpg
Example: 123/123.jpg
```

### 5. Verify in Database
```
Go to: SQL Editor

Run:
SELECT user_id, username, profile_image_url FROM users 
WHERE username = 'testuser';

✅ profile_image_url should be: https://xgcitilgtmxtfcxpmfiz.supabase.co/storage/v1/object/public/profile_image/{user_id}/{user_id}.jpg
❌ NOT: "placeholder_profile_image"
```

---

## 📱 Test Profile Tab Update

```
1. Log in with the account just created
2. Go to Profile tab
3. Click on profile image
4. Select "Change Profile Photo"
5. Choose from Gallery
6. Wait for upload
7. Image should update immediately
```

---

## 📊 Success Indicators

### Logs Show:
```
✅ "Attempting to upload profile image for user: 123"
✅ "Image bytes read successfully. Size: 245678 bytes"
✅ "Uploading to Storage: 123/123.jpg with upsert=true"
✅ "Upload completed successfully for path: 123/123.jpg"
✅ "Public URL generated: https://..."
✅ "Upload success. URL: https://..."
✅ "Verification successful - ProfileImageUrl: https://..."
```

### Storage Shows:
```
✅ profile_images bucket exists
✅ Folder: 123 (with user_id number)
✅ File: 123.jpg inside the folder
✅ File preview shows the uploaded image
```

### Database Shows:
```
✅ profile_image_url is NOT "placeholder_profile_image"
✅ profile_image_url is a full HTTPS URL starting with:
   https://xgcitilgtmxtfcxpmfiz.supabase.co/storage/v1/object/public/profile_image/
✅ URL matches the file in Storage bucket
```

---

## ❌ Failure Indicators

| Log | Problem | Fix |
|-----|---------|-----|
| "Failed to read image bytes" | File permissions | Check gallery access permission |
| "User ID not found" | User not in DB | Run initial save first |
| "Failed to generate public URL" | Bucket issue | Verify bucket exists in Supabase |
| "Error updating database" | RLS policy | Check Supabase RLS policies |
| "placeholder_profile_image" in DB | Update didn't save | Check database permissions |

---

## 🔧 Common Fixes

### Fix 1: If image not appearing after upload
```
1. Check Logcat for errors
2. Verify Supabase credentials in DeclareDatabase.kt
3. Clear app cache: Settings → Apps → SpendHound → Storage → Clear Cache
4. Restart app
```

### Fix 2: If database URL shows "placeholder"
```
1. Check logs for database update errors
2. Go to Supabase → users table
3. Verify profile_image_url column exists
4. Check RLS policies allow UPDATE
```

### Fix 3: If image uploads but doesn't display
```
1. Verify the URL is valid - copy it to browser
2. Check Glide is using correct cache settings
3. Force clear Glide cache: imageSignature = System.currentTimeMillis()
```

---

## 📝 Log Collection Steps

If still having issues, collect logs:

```
1. Connect device/emulator to computer
2. Open Android Studio → Logcat
3. Filter by: "SignUpActivity" or "ProfileFragment"
4. Reproduce the issue
5. Copy all logs
6. Include in bug report
```

---

## 🎯 Expected Success Timeline

| Step | Time | Status |
|------|------|--------|
| Image selection | Immediate | ✅ Image shows in preview |
| Upload to storage | 1-3 sec | ✅ Logs show upload completed |
| Database update | 1-2 sec | ✅ Logs show verification success |
| Display refresh | Immediate | ✅ Image updates in UI |

---

## 📞 Support Info

### If You See These Errors in Logs:

**"StorageException"**
- Check Supabase credentials
- Verify profile_images bucket exists
- Check internet connection

**"PostgrestException"**
- Check RLS policies on users table
- Verify user_id column exists in users table
- Check user is authenticated

**"NullPointerException"**
- This should be fixed - report if it appears
- Logs should now show proper null checks

---

**Last Updated:** March 25, 2026
**Status:** Ready for Testing ✅
