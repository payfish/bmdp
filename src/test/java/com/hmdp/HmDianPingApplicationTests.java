package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIDGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl service;

    @Resource
    private RedisIDGenerator redisIDGenerator;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Test
    public void testIDGenerator() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    long id = redisIDGenerator.nextId("order");
                    System.out.println("id = " + id);
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        long cost = end - begin;
        System.out.println("耗时：" + cost);
    }

}
