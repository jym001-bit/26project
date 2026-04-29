package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    //唯一ID生成器
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override

    public Result seckillVoucher(Long voucherId) {

        //查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //查询开始
        //优惠卷开始时间 在当前时间之后 说明还没开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){

            return Result.fail("秒杀尚未开始！");
        }
        //结束时间在当前时间之前 已经结束了
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束！");
        }

        //查询结束

        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
            //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();



        if (!isLock){
            //失败
            return Result.fail("一人只允许下一单，不可以重复下单");
        }

        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            lock.unlock();
        }


    }



    @Transactional()
    public  Result createVoucherOrder(Long voucherId) {
        //一人一单判断
        //查询订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断是否存在
        if (count > 0) {
            //存在
            //用户已经下单了
            return Result.fail("用户已经购买过一次了");
        }
        //下单
        //解决超卖问题
        //扣减库存 ,使用乐观锁 CAS只要当前的stock值跟我查询到的值相等，说明之前没人改动过，可以更新
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .gt("stock", 0)
                .eq("voucher_id", voucherId)
                .update();
        //创建订单
        if(!success){
            //扣减失败
            return Result.fail("库存不足！");
        }

        //返回订单ID 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户ID获取

        voucherOrder.setUserId(userId);
        //订单卷ID
        voucherOrder.setVoucherId(voucherId);
        //订单新增
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
