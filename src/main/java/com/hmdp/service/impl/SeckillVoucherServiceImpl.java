package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.SeckillVoucherRedis;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.SECKILL_MAP_KEY;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
@Slf4j
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Resource
    private RedisIDGenerator redisIDGenerator;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
    }

    /**
     * 基于redis+消息队列实现异步秒杀
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
        //走到这里代表有秒杀资格
        long orderId = redisIDGenerator.nextId("order");
        //生成优惠券订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        //将订单发布到消息队列中，VoucherOrderListener中实现监听
        try {
            rabbitTemplate.convertAndSend("secKill.topic", "secKill.success", voucherOrder);
        } catch (Exception e) {
            log.error("秒杀成功的消息发送失败，支付单id：{}， 优惠券id：{}", orderId, voucherId, e);
        }
        //直接返回前端
        return Result.ok(orderId);
    }
}
