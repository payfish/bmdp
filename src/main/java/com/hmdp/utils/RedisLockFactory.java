package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLockFactory {

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock createLock(String name) {
        return new RedisDistributedLock(name, redisTemplate);
    }
}
