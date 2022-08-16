package Itd.tan.mall.common.annotation;

import Itd.tan.mall.common.enums.LimitType;

import java.lang.annotation.*;


/**
 * @Author dxl
 * @Description 限流注解
 * @Date 22:34 2022/8/10
 **/
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LimitPoint {
    /**
     * 资源的名字 无实际意义，但是可以用于排除异常
     */
    String name() default "";

    /**
     * 资源的key
     * 如果下方 limitType 值为自定义，那么全局限流参数来自于此
     * 如果limitType 为ip，则限流条件 为 key+ip
     */
    String key() default "";

    /**
     * Key的前缀
     */
    String prefix() default "";

    /**
     * 给定的时间段 单位秒
     */
    int period() default 60;

    /**
     * 最多的访问限制次数
     */
    int limit() default 10;

    /**
     * 类型  ip限制 还是自定义key值限制
     * 建议使用ip，自定义key属于全局限制，ip则是某节点设置，通常情况使用IP
     */
    LimitType limitType() default LimitType.IP;
}
