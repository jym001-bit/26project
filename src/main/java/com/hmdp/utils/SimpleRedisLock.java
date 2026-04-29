package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
//使用Redis实现分布式锁，解决多台jvm的问题使用唯一的锁监视器
public class SimpleRedisLock implements ILock {

    private String name;

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + '-';
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean tryLock(Long timeoutSec) {
        //获取线程标识 使用UUID和当前线程号 做唯一标识
        String id =ID_PREFIX +  Thread.currentThread().getId();

        //获取锁
        String key = KEY_PREFIX + name;
        //使用setnx保持锁的互斥效果 ex设置TTL时间超时时间
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, id + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);

    }

//    @Override
//    public void unLock() {
//        //获取线程标识 是否一致
//        String id =ID_PREFIX +  Thread.currentThread().getId();
//        //锁中标识
//        String Id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断当前锁和Redis里面的锁是否一致
//        if(id.equals(Id)){
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//
//        }
//
//
//
//    }
    @Override
    public void unLock(){
        //使用Lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX +  Thread.currentThread().getId());
    }
}
