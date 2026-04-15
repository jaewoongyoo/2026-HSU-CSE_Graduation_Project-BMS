package com.han.battery.data.common

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 메모리 캐시를 관리하는 클래스
 * API 응답을 메모리에 저장하여 불필요한 네트워크 요청 감소
 */
class CacheManager<K, V> {
    private val cache = mutableMapOf<K, CacheEntry<V>>()
    private val mutex = Mutex()
    private val TAG = "CacheManager"

    data class CacheEntry<T>(
        val value: T,
        val timestamp: Long,
        val ttlMs: Long // Time To Live (밀리초)
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > ttlMs
        }
    }

    /**
     * 캐시에 값 저장
     */
    suspend fun put(key: K, value: V, ttlMs: Long = 5 * 60 * 1000) { // 기본값: 5분
        mutex.withLock {
            cache[key] = CacheEntry(value, System.currentTimeMillis(), ttlMs)
            Log.d(TAG, "✅ 캐시 저장: $key (TTL: ${ttlMs}ms)")
        }
    }

    /**
     * 캐시에서 값 조회
     */
    suspend fun get(key: K): V? {
        return mutex.withLock {
            val entry = cache[key]
            return when {
                entry == null -> {
                    Log.d(TAG, "⚠️ 캐시 미스: $key")
                    null
                }
                entry.isExpired() -> {
                    Log.d(TAG, "⏰ 캐시 만료: $key")
                    cache.remove(key)
                    null
                }
                else -> {
                    Log.d(TAG, "✅ 캐시 히트: $key")
                    entry.value
                }
            }
        }
    }

    /**
     * 특정 키의 캐시 삭제
     */
    suspend fun remove(key: K) {
        mutex.withLock {
            cache.remove(key)
            Log.d(TAG, "🗑️ 캐시 삭제: $key")
        }
    }

    /**
     * 모든 캐시 삭제
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
            Log.d(TAG, "🗑️ 전체 캐시 삭제")
        }
    }

    /**
     * 만료된 캐시 정리
     */
    suspend fun cleanup() {
        mutex.withLock {
            val initialSize = cache.size
            cache.entries.removeAll { it.value.isExpired() }
            val removedCount = initialSize - cache.size
            Log.d(TAG, "Cleanup expired cache: $removedCount removed")
        }
    }

    /**
     * 캐시 상태 조회
     */
    suspend fun getStats(): String {
        return mutex.withLock {
            "캐시 항목 수: ${cache.size}"
        }
    }
}

/**
 * 배터리 관련 캐시 매니저 (싱글톤)
 */
object BatteryCacheManager {
    private val batteryListCache = CacheManager<String, Any>()

    suspend fun cacheBatteryList(batteries: List<Any>) {
        batteryListCache.put("all_batteries", batteries, 10 * 60 * 1000) // 10분
    }

    suspend fun getBatteryListCache(): List<Any>? {
        @Suppress("UNCHECKED_CAST")
        return batteryListCache.get("all_batteries") as? List<Any>
    }

    suspend fun invalidateBatteryListCache() {
        batteryListCache.remove("all_batteries")
    }

    suspend fun clearAllCache() {
        batteryListCache.clear()
    }
}

/**
 * 사용자 관련 캐시 매니저 (싱글톤)
 */
object UserCacheManager {
    private val userCache = CacheManager<String, Any>()

    suspend fun cacheUserProfile(userId: Int, userProfile: Any) {
        userCache.put("user_$userId", userProfile, 15 * 60 * 1000) // 15분
    }

    suspend fun getUserProfileCache(userId: Int): Any? {
        return userCache.get("user_$userId")
    }

    suspend fun invalidateUserProfileCache(userId: Int) {
        userCache.remove("user_$userId")
    }

    suspend fun cacheUserList(users: List<Any>) {
        userCache.put("all_users", users, 10 * 60 * 1000) // 10분
    }

    suspend fun getUserListCache(): List<Any>? {
        @Suppress("UNCHECKED_CAST")
        return userCache.get("all_users") as? List<Any>
    }

    suspend fun invalidateUserListCache() {
        userCache.remove("all_users")
    }

    suspend fun clearAllCache() {
        userCache.clear()
    }
}

