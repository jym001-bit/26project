package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CatchClient {
    private final StringRedisTemplate stringRedisTemplate;
    public CatchClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //使用泛型
    public <R,ID>R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;

        //1.从redis查询商品缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //存在吗
        if (StrUtil.isNotBlank(json))
        {
            return JSONUtil.toBean(json,type);
        }
        //不存在
        //判断是否为空值 ！=null也不是有效数据 就是为空值 因为前面已经判断存不存在了，现在只剩下空值了
        if(json != null)
        {
            return null;
        }
        //为null
        //不存在 返回错误 查询数据库
        R r = dbFallback.apply(id);

        if (r == null)
        {

            //将空值写入redis 不写易造成缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //数据库不存在，返回错误
            return null;
        }
        //存在，写入redis用String json格式,设置 30min 有效期
        this.set(key, r, time, unit);
        //返回
        return r;
    }


}
