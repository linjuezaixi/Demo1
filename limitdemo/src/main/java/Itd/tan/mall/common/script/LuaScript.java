package Itd.tan.mall.common.script;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * @Author dxl
 * @Description //redis脚本
 * @Date 13:14 2022/8/11
 **/
@Configuration
public class LuaScript {

    /**
     * 流量限制脚本
     * @return
     */
    @Bean
    public DefaultRedisScript<Long> limitScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("script/limit2.lua")));
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
