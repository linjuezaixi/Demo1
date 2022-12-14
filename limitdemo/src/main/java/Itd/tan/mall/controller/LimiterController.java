package Itd.tan.mall.controller;

import Itd.tan.mall.common.annotation.LimitPoint;
import Itd.tan.mall.common.enums.LimitType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class LimiterController {
    private static final AtomicInteger ATOMIC_INTEGER_1 = new AtomicInteger();
    private static final AtomicInteger ATOMIC_INTEGER_2 = new AtomicInteger();
    private static final AtomicInteger ATOMIC_INTEGER_3 = new AtomicInteger();

    /**
     * @author fu
     * @description
     * @date 2020/4/8 13:42
     */
    @LimitPoint(key = "limitTest", period = 10, limit = 3)
    @GetMapping("/limitTest1")
    public int testLimiter1() {

        return ATOMIC_INTEGER_1.incrementAndGet();
    }

    /**
     * @author fu
     * @description
     * @date 2020/4/8 13:42
     */
    @LimitPoint(key = "customer_limit_test", period = 10, limit = 3, limitType = LimitType.CUSTOMER)
    @GetMapping("/limitTest2")
    public int testLimiter2() {

        return ATOMIC_INTEGER_2.incrementAndGet();
    }

    /**
     * @author fu
     * @description
     * @date 2020/4/8 13:42
     */
    @LimitPoint(key = "ip_limit_test", period = 10, limit = 3, limitType = LimitType.IP)
    @GetMapping("/limitTest3")
    public int testLimiter3() {

        return ATOMIC_INTEGER_3.incrementAndGet();
    }

}