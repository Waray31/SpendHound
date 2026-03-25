# Profile Image Upload Flow - Visual Diagrams

## 🔄 Sign-Up Step 2 Image Upload Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    SIGN-UP STEP 2 FLOW                          │
└─────────────────────────────────────────────────────────────────┘

User Interface Layer:
┌──────────────────────┐
│  Select Image Button │
│   (onAddProfile      │
│   ImageClicked)      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────────────────┐
│  Image Picker Intent (Gallery/Camera)    │
└──────────────────────┬───────────────────┘
                       │
                       ▼
            [User selects image]
                       │
                       ▼
         ┌─────────────────────────────┐
         │ onActivityResult receives   │
         │ profileImageUri             │
         │ + Load preview via Glide    │
         └─────────┬───────────────────┘
                   │
                   ▼
          ┌──────────────────────────┐
          │  Click "Sign Up" Button  │
          │  (onSignUpClicked)       │
          └──────────┬───────────────┘
                     │
                     ▼

Application Logic Layer:
         ┌──────────────────────────────────────────┐
         │ completeSignUp() - Validates input       │
         │ - Check username not taken               │
         │ - Check profile image selected           │
         └──────────┬───────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────────────────────────┐
         │ uploadProfileImageAndGetUrl()            │
         │ ENHANCED WITH:                           │
         │ ✅ Log user ID                          │
         │ ✅ Read image bytes                     │
         │ ✅ Verify byte size                     │
         │ ✅ Upload to Storage bucket             │
         │ ✅ Generate public URL                  │
         │ ✅ Validate URL not empty               │
         │ ✅ Error handling + stack traces        │
         └──────────┬───────────────────────────────┘
                    │
                    ▼

Storage Layer (Supabase):
         ┌──────────────────────────────────────────┐
         │ Supabase Storage - profile_image bucket  │
         │ Upload: {user_id}/{user_id}.jpg          │
         │ Method: bucket.upload(path, bytes,       │
         │         upsert=true)                     │
         │ Response: Public URL                     │
         └──────────┬───────────────────────────────┘
                    │
                    ▼

Application Logic Layer (Continued):
         ┌──────────────────────────────────────────┐
         │ updateUserInDatabase(username, URL)      │
         │ ENHANCED WITH:                           │
         │ ✅ Log before update                    │
         │ ✅ Update users table                   │
         │ ✅ LOG: Verification reads record       │
         │ ✅ Compare expected vs actual URL       │
         │ ✅ Error handling + stack traces        │
         └──────────┬───────────────────────────────┘
                    │
                    ▼

Database Layer (Supabase Postgrest):
         ┌──────────────────────────────────────────┐
         │ UPDATE users                             │
         │ SET username = 'testuser',               │
         │     profile_image_url = 'https://...'    │
         │ WHERE user_id = 123                      │
         │ THEN SELECT to verify                    │
         └──────────┬───────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────────────────────────┐
         │ Cache Update + Success Message           │
         │ + Navigate to MainActivity               │
         └──────────────────────────────────────────┘
```

---

## 📱 Profile Tab Image Update Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                PROFILE TAB UPDATE FLOW                           │
└─────────────────────────────────────────────────────────────────┘

User Interface Layer:
┌──────────────────────────────────────────┐
│  Profile Fragment - View Created         │
│  - Load current profile image            │
│  - Display via Glide                     │
└──────────────────┬───────────────────────┘
                   │
                   ▼
         ┌──────────────────────────────┐
         │  Click Profile Image         │
         │  (setupProfileImageViewClick)│
         └──────────┬───────────────────┘
                    │
                    ▼
         ┌──────────────────────────────┐
         │  Show PopupMenu:             │
         │  - View Photo                │
         │  - Change Photo              │
         └──────────┬───────────────────┘
                    │
                    ▼
         ┌──────────────────────────────┐
         │  User clicks "Change Photo"  │
         │  (showChangeProfilePhotoDialog)
         └──────────┬───────────────────┘
                    │
        ┌───────────┴────────────┐
        │                        │
        ▼                        ▼
  ┌──────────┐           ┌──────────────┐
  │ Take Photo           │ Choose from  │
  │ (Camera)  │           │ Gallery      │
  └────┬─────┘           └──────┬───────┘
       │                        │
       └───────────┬────────────┘
                   │
                   ▼
         ┌──────────────────────────────┐
         │ onActivityResult receives    │
         │ imageUri + Bitmap (if camera)│
         └──────────┬───────────────────┘
                    │
                    ▼

Application Logic Layer:
         ┌──────────────────────────────────────────┐
         │ updateProfilePhoto(imageUri)             │
         │ - Show loading overlay                   │
         │ - Update image signature (cache bypass)  │
         │ - Load preview via Glide                 │
         └──────────┬───────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────────────────────────┐
         │ uploadProfilePhoto(imageUri)             │
         │ ENHANCED WITH:                           │
         │ ✅ Null check for imageUri              │
         │ ✅ Log auth ID                          │
         │ ✅ Fetch numeric user_id                │
         │ ✅ Read image bytes from URI            │
         │ ✅ Verify bytes were read               │
         │ ✅ Log upload path                      │
         │ ✅ Upload with upsert=true              │
         │ ✅ Generate public URL                  │
         │ ✅ Validate URL                         │
         └──────────┬───────────────────────────────┘
                    │
                    ▼

Storage Layer (Supabase):
         ┌──────────────────────────────────────────┐
         │ Supabase Storage - profile_image bucket  │
         │ Upload/Upsert: {user_id}/{user_id}.jpg   │
         │ Response: Updated Public URL             │
         └──────────┬───────────────────────────────┘
                    │
                    ▼

Application Logic Layer (Continued):
         ┌──────────────────────────────────────────┐
         │ Database Update:                         │
         │ ✅ Build update JSON object             │
         │ ✅ Update users.profile_image_url       │
         │ ✅ Verify update succeeded              │
         │ ✅ Compare expected vs actual           │
         └──────────┬───────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────────────────────────┐
         │ Cache & UI Updates:                      │
         │ ✅ Update PayorAdapter cache            │
         │ ✅ Trigger setProfileImage() refresh    │
         │ ✅ Update image signature               │
         │ ✅ Show success message                 │
         │ ✅ Hide loading overlay                 │
         └──────────────────────────────────────────┘
```

---

## 📊 Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                     DATA FLOW OVERVIEW                           │
└──────────────────────────────────────────────────────────────────┘

Device Storage:
     │
     ├─ Gallery Images ◄─────────┐
     │                          │
     ├─ Camera Images ◄─────────┤
     │                          │
     └─ App Cache              │
                               │
                          [User Selects]
                               │
                               ▼
                        ┌──────────────┐
                        │ ImageUri     │
                        │ URI:content:// │
                        └──────┬───────┘
                               │
                               ▼
                        ┌──────────────────┐
                        │ Read Bytes       │
                        │ Size validation  │
                        └──────┬───────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
        ┌─────────────────┐         ┌──────────────────┐
        │ Supabase        │         │ Android         │
        │ Storage         │         │ (Glide Cache)   │
        │ Bucket:         │         │                 │
        │ profile_image   │         │ .diskCache      │
        │ /{id}/{id}.jpg  │         │ .signature()    │
        └────────┬────────┘         └──────┬───────────┘
                 │                        │
                 └────────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Public URL Generated │
                   │ https://...public/   │
                   │ profile_image/...    │
                   └──────────┬───────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │ Supabase Database - users table         │
        │ profile_image_url: (PUBLIC_URL)         │
        │ WHERE user_id = {numeric_id}            │
        └─────────────────────────────────────────┘
```

---

## 🔐 Error Handling Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                 ERROR HANDLING FLOW                              │
└──────────────────────────────────────────────────────────────────┘

Any Error Occurs:
        │
        ▼
    ┌─────────────────────────────────┐
    │ Catch Exception Block:          │
    │ try { ... } catch(e: Exception) │
    └────────────┬────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
        ▼                 ▼
    ┌────────────┐    ┌──────────────────┐
    │ Log Error: │    │ Show Toast to    │
    │ e.message  │    │ User:            │
    │            │    │ "Upload failed:  │
    │ Log Trace: │    │  {error message}"│
    │ e.printSt  │    │                  │
    │ ackTrace() │    │ (MAIN THREAD)    │
    │            │    │                  │
    │ (IO        │    └────────────────┬─┘
    │ THREAD)    │                     │
    └────────────┘                     │
                 │                     │
                 └────────────────────┬┘
                                      │
                                      ▼
                          ┌──────────────────────┐
                          │ Hide Loading Overlay│
                          │ Re-enable Button    │
                          │ Allow User Retry    │
                          └──────────────────────┘

Success Path:
        │
        ▼
    ┌──────────────────────────────────┐
    │ Log Success Details:             │
    │ - User ID                        │
    │ - File path                      │
    │ - Public URL generated           │
    │ - DB verification result         │
    └────────────┬─────────────────────┘
                 │
                 ▼
    ┌──────────────────────────────────┐
    │ Show Success Toast to User       │
    │ "Profile Photo Changed/Uploaded" │
    │ (MAIN THREAD)                    │
    └────────────┬─────────────────────┘
                 │
                 ▼
    ┌──────────────────────────────────┐
    │ Update UI:                       │
    │ - Hide loading overlay           │
    │ - Refresh profile image display  │
    │ - Update cache                   │
    └──────────────────────────────────┘
```

---

## 🔍 Verification Steps Flow

```
┌──────────────────────────────────────────────────────────────────┐
│              VERIFICATION FLOW (New Implementation)              │
└──────────────────────────────────────────────────────────────────┘

After Upload to Storage:
        │
        ▼
    ┌──────────────────────────────┐
    │ Generate Public URL          │
    │ bucket.publicUrl(path)       │
    └────────────┬─────────────────┘
                 │
                 ▼
    ┌──────────────────────────────┐
    │ Validate URL:                │
    │ if (url.isEmpty()) {         │
    │   throw Exception()          │
    │ }                            │
    └────────────┬─────────────────┘
                 │
                 ▼

After Database Update:
        │
        ▼
    ┌──────────────────────────────────────┐
    │ READ BACK VERIFICATION (NEW!)        │
    │ SELECT * FROM users                  │
    │ WHERE user_id = {id}                 │
    └────────────┬────────────────────────┘
                 │
                 ▼
    ┌──────────────────────────────────────┐
    │ Compare Values:                      │
    │ Expected URL: {uploaded_url}         │
    │ Actual URL: {read_from_db}           │
    │ Match? YES → Success                 │
    │ Match? NO  → Log Warning             │
    └────────────┬────────────────────────┘
                 │
                 ▼
    ┌──────────────────────────────────────┐
    │ Log Verification Result              │
    │ (Shown in Logcat for debugging)      │
    └──────────────────────────────────────┘
```

---

## 📈 Logging Hierarchy

```
LEVEL 1 - Entry Point:
┌─────────────────────────────────────────────┐
│ "Starting profile image upload for user ID" │
│ → Confirm process started                   │
└─────────────────────────────────────────────┘

LEVEL 2 - Data Validation:
┌─────────────────────────────────────────────┐
│ "Image bytes read successfully. Size: X MB" │
│ → Confirm data was retrieved                │
└─────────────────────────────────────────────┘

LEVEL 3 - Storage Operation:
┌─────────────────────────────────────────────┐
│ "Uploading to Storage: path/file.jpg"       │
│ "Upload completed successfully"             │
│ → Confirm storage write                     │
└─────────────────────────────────────────────┘

LEVEL 4 - URL Generation:
┌─────────────────────────────────────────────┐
│ "Public URL generated: https://..."         │
│ → Confirm URL was created                   │
└─────────────────────────────────────────────┘

LEVEL 5 - Database Write:
┌─────────────────────────────────────────────┐
│ "Updating user in Database"                 │
│ "Database update executed"                  │
│ → Confirm database write initiated          │
└─────────────────────────────────────────────┘

LEVEL 6 - Verification:
┌─────────────────────────────────────────────┐
│ "Verification successful - URL matches"     │
│ → Confirm data persistence                  │
└─────────────────────────────────────────────┘

ERROR LEVELS:
┌─────────────────────────────────────────────┐
│ E/Tag: "Storage Error: {message}"           │
│ E/Tag: "Error updating user: {message}"     │
│ → Stack trace for debugging                 │
└─────────────────────────────────────────────┘
```

---

**Last Updated:** March 25, 2026

