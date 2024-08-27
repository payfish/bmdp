package com.hmdp.utils;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class VoucherOrderListener {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "secKill.success.queue"),
            exchange = @Exchange(name = "secKill.topic"),
            key = "secKill.success"
    ))
    public void onMessage(VoucherOrder voucherOrder) {
        try {
            handlerVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("处理订单有异常发生：", e);
            // 根据业务逻辑可以考虑消息重试或者放入死信队列
        }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //理论上redis+lua脚本已经实现了一人一单的功能，同一个用户不可能走到这里，但是还是留着兜底用
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLocked = lock.tryLock();

        if (!isLocked) {
            log.error("用户重复下单，用户ID：{}", userId);
            return;
        }

        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
}
