package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CatchClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

//实现使用缓存查询店铺，不用查询数据库
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CatchClient catchClient;
    @Override
    public Result queryById(Long id) {
        //使用工具类 解决缓存穿透
            //Shop shop = catchClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,id2 -> getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //使用工具类结局缓存击穿
       //Shop shop =  catchClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,id2 -> getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿与缓存穿透
        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿&&
        //Shop shop = queryWithLogicalExpire(id);
        if(shop==null){
           return Result.fail("店铺不存在");
        }

        return Result.ok(shop);

    }

    public Shop queryWithMutex(Long id){
        //线程KEY
        String key = CACHE_SHOP_KEY + id;

        //1.从redis查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //存在吗
        if (StrUtil.isNotBlank(shopJson))
        {
            //存在，return
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //不存在
        //判断是否为空值 ！=null也不是有效数据 就是为空值 因为前面已经判断存不存在了，现在只剩下空值了
        //实现缓存重建
        //获取互斥锁
        //判断成功失败
        //失败休眠
        String LockKey = "lock:shop" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLockShop(LockKey);
            if (!isLock){
                //失败,休眠 50ms
                Thread.sleep(50);
                return queryWithMutex(id);//递归调用
            }
            //获取锁成功 查询数据库
            shop = getById(id);
//            //模拟延迟
//            Thread.sleep(200);
            //缓存穿透
            //不存在
            if (shop == null)
            {

                //将空值写入redis 不写易造成缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //数据库不存在，返回错误
                return null;
            }
            //存在，写入redis用String json格式,设置 30min 有效期
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLockShop(LockKey);
        }


    }
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){

        String key = CACHE_SHOP_KEY + id;

        //1.从redis查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //存在吗
        if (StrUtil.isBlank(shopJson))
        {
            //不存在，return
            return null;
        }
        //存在 JSON 反序列化对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data =(JSONObject) redisData.getData();

        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //之后 未过期 当前时间在expireTime之后
            return shop;
        }
        //过期
        //缓存重建 建立互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLockShop(lockKey);
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //缓存重建
                    this.saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLockShop(lockKey);
                }

            });
        }

        //返回
        return shop;
    }


    //穿透
    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;

        //1.从redis查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //存在吗
        if (StrUtil.isNotBlank(shopJson))
        {
            //存在，return
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //不存在
        //判断是否为空值 ！=null也不是有效数据 就是为空值 因为前面已经判断存不存在了，现在只剩下空值了
        if(shopJson != null)
        {
            return null;
        }
        //为null
        //不存在 返回错误 查询数据库
        Shop shop = getById(id);

        if (shop == null)
        {

            //将空值写入redis 不写易造成缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //数据库不存在，返回错误
            return null;
        }
        //存在，写入redis用String json格式,设置 30min 有效期
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }


    //解决缓存击穿问题，自定义锁，保证只有一个进程在去数据库拿去数据

    //redis使用的是单线程 自定义锁 获取锁
    private boolean tryLockShop(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }
    //释放锁
    private void unLockShop(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //写入Redis 保证key的永久有效 自己控制存在逻辑时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));

    }


    //懒加载
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null)
        {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY +  shop.getId());
        return Result.ok();
    }
}
