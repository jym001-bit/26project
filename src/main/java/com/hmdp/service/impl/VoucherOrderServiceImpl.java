package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    //唯一ID生成器
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional()
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
            return Result.fail("描述已经结束！");
        }

        //查询结束

        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足！");
        }

        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).update();
        //创建订单
        if(!success){
            //扣减失败
            return Result.fail("库存不足！");
        }
        //返回订单ID
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户ID获取
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //订单卷ID
        voucherOrder.setVoucherId(voucherId);
        //订单新增
        save(voucherOrder);
        return Result.ok(orderId);

    }
}
