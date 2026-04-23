package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
//基于redis实现的全局唯一ID模拟雪花算法
@Component
public class RedisIdWorker {
    //开始时间戳 2022 1 1 0 0 0 到 1970 年 1 月 1 日 0 点
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    //序列号位数
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        //时间戳生成 31   s  当前时间 - 起始时间
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        //序列号生成32  redis 序列号自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);


        //时间戳左移32位给序列号 | 拼接
        return timestamp << COUNT_BITS | count;
    }

}
