package com.waray.spendhound.data.repository

import android.util.Log
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
    Log.d("CrewDebug", "cachedFlow START key=$key")
    val cached = jsonBlobDao().get(key)
    Log.d("CrewDebug", "cachedFlow cached=${cached != null} key=$key")
    if (cached != null) {
        @Suppress("UNCHECKED_CAST")
        val value = gson.fromJson<T>(cached.json, type)
        Log.d("CrewDebug", "cachedFlow emitting CACHED value key=$key")
        emit(value)
    }
    val isStale = cached == null || (System.currentTimeMillis() - cached.fetchedAt) > staleTtlMs
    Log.d("CrewDebug", "cachedFlow isStale=$isStale key=$key")
    if (isStale) {
        Log.d("CrewDebug", "cachedFlow fetching FRESH key=$key")
        val fresh = fetch()
        jsonBlobDao().upsert(CachedJsonBlob(key, gson.toJson(fresh), System.currentTimeMillis()))
        Log.d("CrewDebug", "cachedFlow emitting FRESH key=$key")
        emit(fresh)
    }
    Log.d("CrewDebug", "cachedFlow COMPLETE key=$key")
}.flowOn(Dispatchers.IO)

inline fun <reified T> typeOf(): java.lang.reflect.Type = object : TypeToken<T>() {}.type
