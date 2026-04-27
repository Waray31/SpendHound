package com.waray.spendhound.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.data.local.CachedJsonBlob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal val gson = Gson()

fun <T> AppDatabase.cachedFlow(
    key: String,
    staleTtlMs: Long,
    type: java.lang.reflect.Type,
    fetch: suspend () -> T
): Flow<T> = flow {
    val cached = jsonBlobDao().get(key)
    if (cached != null) {
        @Suppress("UNCHECKED_CAST")
        emit(gson.fromJson<T>(cached.json, type))
    }
    val isStale = cached == null || (System.currentTimeMillis() - cached.fetchedAt) > staleTtlMs
    if (isStale) {
        val fresh = fetch()
        jsonBlobDao().upsert(CachedJsonBlob(key, gson.toJson(fresh), System.currentTimeMillis()))
        emit(fresh)
    }
}.flowOn(Dispatchers.IO)

inline fun <reified T> typeOf(): java.lang.reflect.Type = object : TypeToken<T>() {}.type
