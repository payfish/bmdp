package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheUtil cacheUtil;

    @Override
    public Result queryById(Long id) {

        Shop shop = cacheUtil.getObject(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

//    /**
//     * 基于互斥锁根据店铺id查询店铺
//     * @param id
//     * @return
//     */
//    private Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        if (shopJson != null) {
//            return null;
//        }
//        //实现缓存重建
//        String lockKey = "lock:shop:" + id;
//        Shop shop;
//
//        try {
//            boolean lock = tryLock(lockKey);
//
//            if (!lock) { //获取锁失败，休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //成功获取锁,double check检查redis
//            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson2)) {
//                return JSONUtil.toBean(shopJson2, Shop.class);
//            }
//
//            shop = getById(id);
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unlock(lockKey);
//        }
//        return shop;
//    }




    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }
        // 1、更新数据库
        updateById(shop);

        // 2、删除缓存

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ConcurrentHashMap map = new ConcurrentHashMap();
        CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
        map.put(1,1);
        AtomicInteger atomicInteger = new AtomicInteger(0);
        ArrayBlockingQueue arrayBlockingQueue = new ArrayBlockingQueue(10);
        Deque queue = new ArrayDeque();
        LockSupport.park();
        return Result.ok();
    }




}
