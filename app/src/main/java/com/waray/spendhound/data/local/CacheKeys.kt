package com.waray.spendhound.data.local

object CacheKeys {
    fun home(userId: Long) = "home_$userId"
    fun homeRecent(userId: Long) = "home_recent_$userId"
    fun profile(userId: Long) = "profile_$userId"
    fun profileGroups(userId: Long) = "profile_groups_$userId"
    fun transactions(userId: Long) = "transactions_$userId"
    fun borrows(userId: Long) = "borrows_$userId"
    fun groupsList(userId: Long) = "groups_list_$userId"
    fun groupDetail(groupId: Long) = "group_detail_$groupId"
    fun groupExpenses(groupId: Long) = "group_expenses_$groupId"
    fun messageReads(groupId: Long) = "message_reads_$groupId"
    fun transactionReads(groupId: Long) = "tx_reads_$groupId"

    const val STALE_HOME = 5 * 60 * 1000L
    const val STALE_PROFILE = 10 * 60 * 1000L
    const val STALE_GROUPS_LIST = 5 * 60 * 1000L
    const val STALE_GROUP_DETAIL = 5 * 60 * 1000L
    const val STALE_TRANSACTIONS = 5 * 60 * 1000L
    const val STALE_BORROWS = 5 * 60 * 1000L
    const val STALE_READS = 30 * 1000L
}
