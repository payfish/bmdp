package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RedisDistributedLock implements ILock{

    private String name;
    private StringRedisTemplate redisTemplate;
    private static final String keyPrefix = "lock:";
    private static final String idPrefix = UUID.randomUUID().toString(true) + "-";

    private static DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
    }

    public RedisDistributedLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        String lockKey = keyPrefix + name;
        String value = idPrefix + Thread.currentThread().getId();
        Boolean b = redisTemplate.opsForValue().setIfAbsent(lockKey, value, timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
//        return BooleanUtil.isTrue(b);
    }


    @Override
    public void unlock() {
        redisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(keyPrefix + name),
                idPrefix + Thread.currentThread().getId()
        );
    }
}
