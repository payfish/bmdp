package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Save any java object with TTL
     *
     * @param key      The key of the object
     * @param object   Object to save to redis
     * @param timeOut  TTL of the object
     * @param timeUnit TimeUnit of TTL
     */
    public void saveObject(String key, Object object, long timeOut, TimeUnit timeUnit) {
        if (object == null) {
            log.error("请勿缓存空对象");
            return;
        }
        String jsonStr = JSONUtil.toJsonStr(object);
        stringRedisTemplate.opsForValue().set(key, jsonStr, timeOut, timeUnit);
    }

    /**
     * Save Object With Logic Expire Time
     *
     * @param key     The key of the object
     * @param object  Object to save to redis
     * @param timeOut Use LocalDateTime.now() to plus this timeout to be the logic expire time of the object
     */
    public String saveObjectWithLogicExpire(String key, Object object, long timeOut, TimeUnit timeUnit) {
        if (object == null) {
            log.error("请勿缓存空对象");
            return "";
        }
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(timeOut)), object);
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
        return "成功";
    }


    /**
     * Query object with cache penetration and cache null object instead
     *
     * @param keyPrefix
     * @param id
     * @param clazz
     * @param function
     * @param timeOut
     * @param timeUnit
     * @param <T>
     * @param <ID>
     * @return
     */
    public <T, ID> T getObject(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> function,
                               long timeOut, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(jsonStr)) {
            return JSONUtil.toBean(jsonStr, clazz);
        }
        if (null != jsonStr) { // jsonStr = "";查到redis中的空对象，直接返回null，不会再将请求打到数据库
            return null;
        }
        T t = function.apply(id); // 查询数据库或其他操作
        if (null == t) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.saveObject(key, t, timeOut, timeUnit);
        return t;
    }


    /**
     * Query object with cache penetration and cache object with logic expire instead
     * @param keyPrefix
     * @param id
     * @param clazz
     * @param function
     * @param timeOut
     * @param timeUnit
     * @return
     * @param <T>
     * @param <ID>
     */
    public <T, ID> T queryWithLogicExpire(String keyPrefix, ID id, Class<T> clazz, Function<ID, T> function,
                                          long timeOut, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 未命中缓存，直接返回空对象
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }
        // 命中缓存，反序列化json字符串为redisData对象，判断expire time是否过期
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);;
        LocalDateTime expireTime = redisData.getExpireTime();
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        // 未过期则直接返回即可
        if (LocalDateTime.now().isBefore(expireTime)) {
            return t;
        }

        // 过期，尝试缓存重建
        String lockKey = LOCK_SHOP_KEY + id;

        boolean lock = tryLock(lockKey);
        //获取锁成功，开启独立线程，执行缓存重建
        if (lock) {
            //先double check是否有未过期的缓存存在（被其他线程重建过的）,存在直接返回
            String s = stringRedisTemplate.opsForValue().get(key);
            RedisData r1 = JSONUtil.toBean(s, RedisData.class);
            if (LocalDateTime.now().isBefore(r1.getExpireTime())) {
                return JSONUtil.toBean((JSONObject) r1.getData(), clazz);
            }
            // 不存在，创建新线程执行缓存重建
            Future<String> future = CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T t1 = function.apply(id);
                    return this.saveObjectWithLogicExpire(key, t1, timeOut, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
            try {
                String s1 = future.get();
                System.out.println(s1);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

        }
        return t;
    }
//    /**
//     * 缓存预热
//     * @param id
//     * @param expireTime
//     */
//    public String saveShop2Redis(Long id, Long expireTime) {
//        Shop shop = getById(id);
//        com.hmdp.entity.RedisData redisData = new com.hmdp.entity.RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
//        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//        return Thread.currentThread().getName() + " 执行缓存重建成功！";
//    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            5,
            Runtime.getRuntime().availableProcessors() + 1,
            10L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>());
    /**
     * 加锁方法
     * @param key
     * @return true if succeed
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 解锁方法
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}

