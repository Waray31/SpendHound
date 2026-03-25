package com.waray.spendhound

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Diagnostic tool for Supabase storage issues
 */
object StorageDiagnostics {
    private const val TAG = "StorageDiagnostics"
    
    suspend fun diagnoseStorageIssues(context: Context): DiagnosticReport {
        return withContext(Dispatchers.IO) {
            val report = DiagnosticReport()
            
            try {
                Log.d(TAG, "=== Starting Storage Diagnostics ===")
                
                // Check 1: Supabase client initialized
                try {
                    val client = DeclareDatabase.client
                    report.clientInitialized = true
                    Log.d(TAG, "✓ Supabase client is initialized")
                } catch (e: Exception) {
                    report.clientInitialized = false
                    report.errors.add("Supabase client not initialized: ${e.message}")
                    Log.e(TAG, "✗ Client initialization failed: ${e.message}")
                    return@withContext report
                }
                
                // Check 2: Get storage instance
                try {
                    val storage = DeclareDatabase.storage
                    report.storageAccessible = true
                    Log.d(TAG, "✓ Storage module is accessible")
                } catch (e: Exception) {
                    report.storageAccessible = false
                    report.errors.add("Cannot access storage: ${e.message}")
                    Log.e(TAG, "✗ Storage access failed: ${e.message}")
                    return@withContext report
                }
                
                // Check 3: Get bucket reference
                try {
                    val bucket = DeclareDatabase.profileImagesBucket
                    report.bucketAccessible = true
                    Log.d(TAG, "✓ profile_image bucket reference obtained")
                } catch (e: Exception) {
                    report.bucketAccessible = false
                    report.errors.add("Cannot access profile_image bucket: ${e.message}")
                    Log.e(TAG, "✗ Bucket access failed: ${e.message}")
                    return@withContext report
                }
                
                // Check 4: Authentication status
                try {
                    val auth = DeclareDatabase.auth
                    val currentUser = auth.currentUserOrNull()
                    if (currentUser != null) {
                        report.authenticated = true
                        report.userId = currentUser.id
                        Log.d(TAG, "✓ User is authenticated: ${currentUser.id}")
                    } else {
                        report.authenticated = false
                        report.errors.add("User is not authenticated")
                        Log.e(TAG, "✗ No authenticated user")
                    }
                } catch (e: Exception) {
                    report.authenticated = false
                    report.errors.add("Auth check failed: ${e.message}")
                    Log.e(TAG, "✗ Auth check failed: ${e.message}")
                }
                
                // Check 5: Try test upload
                try {
                    Log.d(TAG, "Attempting test upload...")
                    val testPath = "diagnostics/test.txt"
                    val testData = "test".toByteArray()
                    
                    val bucket = DeclareDatabase.profileImagesBucket
                    bucket.upload(testPath, testData, upsert = true)
                    
                    report.testUploadSuccessful = true
                    Log.d(TAG, "✓ Test upload successful to: $testPath")
                    
                    // Try to get public URL
                    try {
                        val publicUrl = bucket.publicUrl(testPath)
                        report.publicUrlGenerated = true
                        report.testPublicUrl = publicUrl
                        Log.d(TAG, "✓ Public URL generated: $publicUrl")
                    } catch (e: Exception) {
                        report.publicUrlGenerated = false
                        report.errors.add("Cannot generate public URL: ${e.message}")
                        Log.e(TAG, "✗ Public URL generation failed: ${e.message}")
                    }
                    
                    // Clean up test file
                    try {
                        bucket.delete(testPath)
                        Log.d(TAG, "✓ Test file cleaned up")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clean up test file: ${e.message}")
                    }
                } catch (e: Exception) {
                    report.testUploadSuccessful = false
                    report.errors.add("Test upload failed: ${e.message}")
                    Log.e(TAG, "✗ Test upload failed: ${e.message}", e)
                }
                
                report.completedSuccessfully = report.errors.isEmpty()
                Log.d(TAG, "=== Diagnostics Complete - Errors: ${report.errors.size} ===")
                
            } catch (e: Exception) {
                report.completedSuccessfully = false
                report.errors.add("Unexpected error during diagnostics: ${e.message}")
                Log.e(TAG, "✗ Unexpected error: ${e.message}", e)
            }
            
            report
        }
    }
    
    data class DiagnosticReport(
        var clientInitialized: Boolean = false,
        var storageAccessible: Boolean = false,
        var bucketAccessible: Boolean = false,
        var authenticated: Boolean = false,
        var userId: String? = null,
        var testUploadSuccessful: Boolean = false,
        var publicUrlGenerated: Boolean = false,
        var testPublicUrl: String? = null,
        var completedSuccessfully: Boolean = false,
        val errors: MutableList<String> = mutableListOf()
    ) {
        fun getDetailedReport(): String {
            return """
                === STORAGE DIAGNOSTIC REPORT ===
                
                Supabase Client: ${if (clientInitialized) "✓" else "✗"}
                Storage Module: ${if (storageAccessible) "✓" else "✗"}
                Bucket Access: ${if (bucketAccessible) "✓" else "✗"}
                Authenticated: ${if (authenticated) "✓" else "✗"}
                User ID: $userId
                Test Upload: ${if (testUploadSuccessful) "✓" else "✗"}
                Public URL: ${if (publicUrlGenerated) "✓" else "✗"}
                Public URL: $testPublicUrl
                
                Issues Found: ${errors.size}
                ${errors.joinToString("\n") { "  - $it" }}
                
                Status: ${if (completedSuccessfully) "ALL CHECKS PASSED ✓" else "ISSUES DETECTED ✗"}
            """.trimIndent()
        }
    }
}

