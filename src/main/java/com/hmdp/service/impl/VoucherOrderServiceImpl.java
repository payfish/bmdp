package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucherRedis;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.RedisLockFactory;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.SECKILL_MAP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIDGenerator redisIDGenerator;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisLockFactory redisLockFactory;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedissonClient redissonClient;

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    private static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
    }

    /**
     * 基于redis+阻塞队列实现异步秒杀
     * @param voucherId
     * @return
     */
    @Override
    public Result secKillByVoucherId(Long voucherId) {
        Map<Object, Object> seckillMap = stringRedisTemplate.opsForHash().entries(SECKILL_MAP_KEY + voucherId);
        SeckillVoucherRedis svr = BeanUtil.mapToBean(seckillMap, SeckillVoucherRedis.class, false);
        if (svr.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (svr.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        Long userId = UserHolder.getUser().getId();
        Long res = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        assert res != null;
        int i = res.intValue();
        if (i != 0) return Result.fail(i == 1 ? "库存不足！" : "一人只能下一单");
        long orderId = redisIDGenerator.nextId("order");
        //阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        queue.add(voucherOrder);
        iVoucherOrderServiceProxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    private IVoucherOrderService iVoucherOrderServiceProxy;

    private static final ExecutorService pool = Executors.newSingleThreadExecutor();

    private BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024 * 1024);

    @PostConstruct
    private void init() {
        pool.submit(() -> {
            while (true) {
                VoucherOrder voucherOrder = null;
                try {
                    voucherOrder = queue.take();
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单有异常发生：", e);
                }
            }
        });
    }

    /**
     * 异步将订单存入数据库
     * @param voucherOrder
     */
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock myLock = redissonClient.getLock("lock:order:" + userId);
        boolean lock;
        lock = myLock.tryLock();
        if (!lock) {
            log.error("不能重复下单");
            return;
        }
        try {
            iVoucherOrderServiceProxy.createVoucherOrder(voucherOrder);
        } finally {
            myLock.unlock();
        }
    }

    /**
    @Override
    public Result secKillByVoucherId(Long voucherId) {
        //查询优惠券
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        //判断库存是否充足
        if (seckillVoucher.getStock() == 0) {
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        RLock myLock = redissonClient.getLock("lock:order:" + userId);
        boolean lock = false;
        lock = myLock.tryLock();

        if (!lock) {
            return Result.fail("一人只能下一单");
        }
        try {
            IVoucherOrderService iVoucherOrderServiceProxy = (IVoucherOrderService) AopContext.currentProxy();
            return iVoucherOrderServiceProxy.createVoucherOrder(voucherId);
        } finally {
            myLock.unlock();
        }
    }**/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("不能重复下单");
        }
        //扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
        }
        save(voucherOrder);
    }
}
