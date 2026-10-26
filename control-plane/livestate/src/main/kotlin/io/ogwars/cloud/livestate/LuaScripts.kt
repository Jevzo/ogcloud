package io.ogwars.cloud.livestate

/** Atomic multi-key operations; every script runs as one Valkey command. */
internal object LuaScripts {
    /** KEYS: record, all-set, group-set. ARGV: data, ttl ms, member. Returns 1 if created. */
    const val CREATE = """
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        redis.call('HSET', KEYS[1], 'rev', 1, 'data', ARGV[1])
        redis.call('PEXPIRE', KEYS[1], ARGV[2])
        redis.call('SADD', KEYS[2], ARGV[3])
        redis.call('SADD', KEYS[3], ARGV[3])
        return 1
    """

    /** KEYS: record. ARGV: expected revision, data, ttl ms. Returns 1 if replaced. */
    const val COMPARE_AND_SET = """
        local rev = redis.call('HGET', KEYS[1], 'rev')
        if not rev or tonumber(rev) ~= tonumber(ARGV[1]) then return 0 end
        redis.call('HSET', KEYS[1], 'rev', tonumber(rev) + 1, 'data', ARGV[2])
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        return 1
    """

    /** KEYS: session, name index, online set. ARGV: data, ttl ms, uuid. Creates or replaces. */
    const val PUT_SESSION = """
        local rev = redis.call('HGET', KEYS[1], 'rev')
        local next = 1
        if rev then next = tonumber(rev) + 1 end
        redis.call('HSET', KEYS[1], 'rev', next, 'data', ARGV[1])
        redis.call('PEXPIRE', KEYS[1], ARGV[2])
        redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[2])
        redis.call('SADD', KEYS[3], ARGV[3])
        return next
    """

    /** KEYS: session, name index, online set. ARGV: uuid. Leaves a name index that now belongs to someone else. */
    const val REMOVE_SESSION = """
        redis.call('DEL', KEYS[1])
        if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]) end
        redis.call('SREM', KEYS[3], ARGV[1])
        return 1
    """

    /** KEYS: lock. ARGV: token. Returns 1 if released by its owner. */
    const val RELEASE_LOCK = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
    """

    /** KEYS: lock. ARGV: token, lease ms. Returns 1 if extended by its owner. */
    const val EXTEND_LOCK = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
        return 0
    """
}
