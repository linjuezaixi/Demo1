package Itd.tan.mall.common.interceptor;

import Itd.tan.mall.common.annotation.LimitPoint;
import Itd.tan.mall.common.enums.LimitType;
import Itd.tan.mall.common.enums.ResultCode;
import Itd.tan.mall.common.exception.ServiceException;
import Itd.tan.mall.common.untils.IpUtils;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.io.Serializable;

/**
 * @Author dxl
 * @Description 切面代码实现
 * @Date 12:35 2022/8/11
 **/
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
