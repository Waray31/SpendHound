# Supabase Configuration Requirements for Profile Image Upload

## Storage Bucket Setup

### Profile Image Bucket Configuration

The `profile_images` bucket must exist and be configured as follows:

**Bucket Name:** `profile_images`

**Bucket Settings:**
- **File size limit:** 10 MB (recommended)
- **Access level:** PUBLIC (read-only via URL)
- **Allow public uploads:** Via authenticated users only

### Storage Policies (SQL)

Run these in Supabase SQL Editor to set up proper permissions:

```sql
-- Allow authenticated users to upload their own profile images
CREATE POLICY "Allow authenticated users to upload profile images"
ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
  bucket_id = 'profile_images'
);

-- Allow authenticated users to update their own profile images (overwrite)
CREATE POLICY "Allow authenticated users to update profile images"
ON storage.objects
FOR UPDATE TO authenticated
USING (bucket_id = 'profile_images')
WITH CHECK (bucket_id = 'profile_images');

-- Allow anyone to view public profile images
CREATE POLICY "Allow public to view profile images"
ON storage.objects
FOR SELECT
USING (bucket_id = 'profile_images');
```

## Database Table Configuration

### Users Table RLS Policies

The following RLS policies must be in place on the `users` table:

```sql
-- Allow users to read their own profile data
CREATE POLICY "Users can read their own profile"
ON public.users
FOR SELECT
TO authenticated
USING (auth_id = auth.uid());

-- Allow users to update their own profile_image_url
CREATE POLICY "Users can update their own profile_image_url"
ON public.users
FOR UPDATE
TO authenticated
USING (auth_id = auth.uid())
WITH CHECK (auth_id = auth.uid());

-- Allow authenticated users to read other users' profile_image_url (for displaying in app)
CREATE POLICY "Allow reading profile images of other users"
ON public.users
FOR SELECT
TO authenticated
USING (true);
```

### Table Structure

Ensure your `users` table has these columns:

```sql
CREATE TABLE public.users (
  user_id bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  auth_id uuid NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  username varchar(255) NOT NULL UNIQUE,
  email varchar(255) NOT NULL,
  password varchar(255),
  profile_image_url varchar(500),
  created_at timestamp with time zone DEFAULT now(),
  updated_at timestamp with time zone DEFAULT now()
);
```

## Verification Checklist

### Step 1: Verify Bucket Exists
```sql
-- Run in Supabase SQL Editor
SELECT name, id, public FROM storage.buckets WHERE name = 'profile_images';

-- Expected output:
-- name: "profile_images"
-- id: (some uuid)
-- public: true
```

### Step 2: Check RLS Policies
```sql
-- Check storage policies
SELECT * FROM storage.buckets WHERE name = 'profile_images';

-- Check storage object policies
SELECT policyname, role, definition FROM pg_policies 
WHERE tablename = 'objects' AND schemaname = 'storage'
AND definition LIKE '%profile_images%';
```

### Step 3: Check Users Table Policies
```sql
SELECT policyname, roles, permissive, cmd, qual, with_check 
FROM pg_policies 
WHERE tablename = 'users' AND schemaname = 'public';
```

## Testing RLS Policies

### Test 1: Upload Image as Authenticated User
```sql
-- This should succeed (simulated - actual upload via app)
SELECT * FROM storage.objects 
WHERE bucket_id = 'profile_images' 
AND owner = auth.uid();  -- Current user's UID
```

### Test 2: Update Profile Image URL
```sql
-- Test update as user
UPDATE public.users 
SET profile_image_url = 'https://example.com/image.jpg'
WHERE auth_id = auth.uid();

-- Should succeed if RLS policy is correct
```

## Troubleshooting

### Issue: "Policy denied access" on upload

**Cause:** Storage bucket doesn't have proper policies
**Solution:**
```sql
-- Verify bucket exists and is public
SELECT * FROM storage.buckets WHERE name = 'profile_images';

-- If not public, update:
UPDATE storage.buckets SET public = true WHERE name = 'profile_images';

-- Verify policies exist:
SELECT * FROM storage.policies WHERE bucket_id IN (SELECT id FROM storage.buckets WHERE name = 'profile_images');
```

### Issue: Cannot read profile_image_url from users table

**Cause:** RLS policy blocks SELECT
**Solution:**
```sql
-- Make sure users can read their own data
SELECT * FROM pg_policies WHERE tablename = 'users' 
AND (policyname LIKE '%read%' OR cmd = 'SELECT');
```

### Issue: Cannot update profile_image_url

**Cause:** RLS policy blocks UPDATE
**Solution:**
```sql
-- Verify UPDATE policy exists
SELECT * FROM pg_policies WHERE tablename = 'users' AND cmd = 'UPDATE';

-- If missing, create it:
CREATE POLICY "Users can update their own profile"
ON public.users
FOR UPDATE
TO authenticated
USING (auth_id = auth.uid())
WITH CHECK (auth_id = auth.uid());
```

## Expected App Behavior with Proper Configuration

### Upload Flow:
1. ✅ User selects image in Sign-up Step 2
2. ✅ App uploads to `profile_images/{user_id}/{user_id}.jpg`
3. ✅ App gets public URL from bucket
4. ✅ App updates `users` table with URL
5. ✅ Image displays immediately with Glide

### Update Flow:
1. ✅ User clicks profile image in Profile tab
2. ✅ User selects new image (camera/gallery)
3. ✅ App uploads to same path (upsert=true)
4. ✅ App gets updated public URL
5. ✅ App updates `users` table
6. ✅ Image refreshes in UI

## Security Considerations

### Public vs Private Access
- **profile_images bucket:** PUBLIC (read-only)
  - Users can view other users' profile images
  - Images served via public URL
  
- **users table:** Protected by RLS
  - Users can only read/update their own data
  - profile_image_url is readable by all authenticated users
  - Only the owner can UPDATE their profile_image_url

### Rate Limiting
Consider implementing rate limiting for uploads:
```sql
-- Add a column to track last upload time (optional)
ALTER TABLE public.users 
ADD COLUMN last_profile_update timestamp with time zone DEFAULT now();

-- Or implement in app level (not in DB)
```

## Maintenance

### Monitor Storage Usage
```sql
-- Check total size of profile images
SELECT SUM(metadata->>'size')::bigint / 1024 / 1024 as size_mb 
FROM storage.objects 
WHERE bucket_id = (SELECT id FROM storage.buckets WHERE name = 'profile_images');
```

### Cleanup Orphaned Files
```sql
-- Find files for deleted users
SELECT obj.name 
FROM storage.objects obj
LEFT JOIN public.users u ON obj.name LIKE u.user_id::text || '%'
WHERE obj.bucket_id = (SELECT id FROM storage.buckets WHERE name = 'profile_images')
AND u.user_id IS NULL;
```

---

**Last Updated:** March 25, 2026
**Supabase Version:** v2+
