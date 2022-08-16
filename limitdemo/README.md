# 限流demo之滑动窗口

## 某电商业务场景2：

运营推广部门某次策划上线秒杀或者优惠活动，经测试人员估算压力测试，大约在一个小时内进来100万+用户访问，系统吞吐量固定的情况下，为保障Java服务端正常运行不崩溃，需要对正常访问用户进行限流处理，大约每秒响应1000个请求。

请问限流的系统如何设计，给出具体的实现？（服务端框架采用spring boot+mybatis+redis）





## 问题分析

1）不使用消息队列系统限流思路可从从两方面考虑：

容器限流

服务端限流

2）使用自定义注解，对需要限流的接口进行自定义使用。相应使用AOP来解析自定义注解。



## 解决

### 容器限流：

Tomcat、Nginx等。

- Tomcat 可以设置最大线程数（maxThreads），当并发超过最大线程数会排队等待执行；
- 而 Nginx 提供了两种限流手段：一是控制速率，二是控制并发连接数；

### 服务端限流：

通过限流算法搭配限流策略。

- 固定窗口算法：固定窗口对于接口限流有缺陷，不能合理处理在两个时间窗口临界条件下的多次请求。比如1.59s-2.00s之间的100次请求，同时2.01s-2.02s又有100次请求。

- 滑动窗口算法：滑动窗口接受完指定数量后则拒绝请求。指定时间T内，只允许发生N次。我们可以将这个指定时间T，看成一个滑动时间窗口（定宽）。我们采用Redis的zset基本数据类型的score来圈出这个滑动时间窗口。在实际操作zset的过程中，我们只需要保留在这个滑动时间窗口以内的数据，其他的数据不处理即可。每个用户的行为采用一个zset存储，score为毫秒时间戳，value也使用毫秒时间戳（比UUID更加节省内存）。只保留滑动窗口时间内的行为记录，如果zset为空，则移除zset，不再占用内存（节省内存）。

- //ARGV[1]：当前时间戳；ARGV[2]：前60s时间戳（当前时间-60s）；ARGV[3]：有效时间

- 漏斗算法： 超过漏斗大小则拒绝。

- 令牌桶算法：推荐。能适应突发暴增的请求。

  在令牌桶算法中有一个程序以某种恒定的速度生成令牌，并存入令牌桶中，而每个请求需要先获取令牌才能执行，如果没有获取到令牌的请求可以选择等待或者放弃执行



## 具体实现及详解：

### 简介：

1.基础配置：依赖、 Redis基本信息、RedisTemplate、lua脚本使用配置、全局异常处理等

2.创建自定义限流注解和枚举类：分CUSTOMER和IP。CUSTOMER即对用户的UUID等进行限流；IP即对某个IP进行限流。

3.编写lua脚本：redis自带lua，使用lua可对redis多个操作一次性处理，保证原子化，保证线程安全。

4.注解解析：使用AOP对自定义限流注解进行解析。

5.接口测试：

### 项目文件说明：

- common

  - annotation：
    - LimitPoint：自定义注解

  - enums：
    - LimitType：限流类型枚举类
    - ResultCode：统一返回码

  - exception
    - ServiceException：统一异常处理

  - interceptor
    - LimitInterceptor：自定义注解解析

  - script
    - LuaScript：lua脚本配置类

  - untils
    - Ip工具类

- config：

  - RedsiConfig：自定义RedisTemplate

- controller

  - 控制层（用于测试）



### 1基础配置：

1）依赖、Redis基本信息、RedisTemplate、lua脚本使用配置、全局异常处理等不做赘述，详情看代码。



### 2.创建自定义限流注解和枚举类

分CUSTOMER和IP。CUSTOMER即对用户的UUID等进行限流；IP即对某个IP进行限流。

```java
public enum LimitType {
    /**
     * 自定义key
     */
    CUSTOMER,

    /**
     * 请求者IP
     */
    IP;
}
```

```java
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
```



### 3.编写lua脚本

#### 介绍

使用滑动窗口解决接口限流问题。每访问一次接口将执行一次lua脚本。

1. 使用Redis的Zset类型，来圈出滑动窗口。score和value都使用请求的时间戳。

   ```
   redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[1])
   ```

2. 查询相应key中score-value的个数，并返回。用以做操作的次数，每请求一次接口将添加一组score-velue。在注解解析中将判断操作次数大于指定的限流次数，则进行限流。

   ```
   local c= redis.call('ZCARD', KEYS[1])
   ```

3. 清除之间时间窗口访问量，此操作是滑动窗口区别固定时间窗口的区别之一。

   ```
   redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[2]))
   ```

4. 设定key的有效时间。

   ```
   redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
   ```

#### 详尽代码

```lua
--KEYS[1]: 限流 key
--ARGV[1]: 当前时间戳（作为score）
--ARGV[2]: 时间戳- 时间窗口
--ARGV[3]: key有效时间

redis.call('ZADD', KEYS[1], tonumber(ARGV[1]), ARGV[1])
local c= redis.call('ZCARD', KEYS[1])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[2]))
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
return c
```



### 4.注解解析

核心思路：利用AOP对注解进行解析

1. Before前置通知解析自定义注解LimitPoint类。才能在接口上使用自定义注解@LimitPoint

2. 引入自定义RedisTemplate、lua脚本使用配置。才能使用自定义的RedisTemplate、lua脚本。

3. 对lua所需参数进行处理

   （lua所需参数：Number count = redisTemplate.execute(limitScript, keys,ts, windowlimit, period);;）

   具体不再赘述，主要需要对keys的特殊处理、以及通过Google的Guava工具类的ImmutableCollection<E>类进行线程安全处理。

4. 对lua返回的值与最大限流次数对比，大于最大限流次数则进行限流

5. 异常处理

```java
@Aspect
@Configuration
@Slf4j
public class LimitInterceptor {
    private RedisTemplate<String, Serializable> redisTemplate;

    private DefaultRedisScript<Long> limitScript;

    @Autowired
    public void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    public void setLimitScript(DefaultRedisScript<Long> limitScript) {
        this.limitScript = limitScript;
    }

 /**
  * @Author dxl
  * @Description //切面
  * @Date 12:44 2022/8/11
  **/
 @Before("@annotation(limitPointAnnotation)")
 public void interceptor(LimitPoint limitPointAnnotation) {
     LimitType limitTypeEnums = limitPointAnnotation.limitType();
     String key;
     int period = limitPointAnnotation.period();
     int limitCount = limitPointAnnotation.limit();
     switch (limitTypeEnums) {
         case CUSTOMER:
             key = limitPointAnnotation.key();
             break;
         default:
             key = limitPointAnnotation.key() + IpUtils
                     .getIpAddress(((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
     }
     ImmutableList<String> keys = ImmutableList.of(StringUtils.join(limitPointAnnotation.prefix(), key));
     try {
        long ts = System.currentTimeMillis();
         ImmutableList<String> windowlimit =ImmutableList.of(String.valueOf((ts - period * 1000))) ;
         Number count = redisTemplate.execute(limitScript, keys,ts, windowlimit, period);
         log.info("限制请求{}, 当前请求{},缓存key{}", limitCount, count.intValue(), keys);
         //如果缓存里没有值，或者他的值小于限制频率
         if (count.intValue() >= limitCount) {
             throw new ServiceException(ResultCode.LIMIT_ERROR);
         }
     }
     //如果从redis中执行都值判定为空，则这里跳过
     catch (NullPointerException e) {
         return;
     } catch (ServiceException e) {
         throw e;
     } catch (Exception e) {
         throw new ServiceException(ResultCode.ERROR);
     }
 }
}
```

### 5.接口测试

不做过多赘述。主要利用原子类来return结果显示。