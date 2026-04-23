package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result shopList() {
        String key = CACHE_SHOP_TYPE_KEY;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在直接返回
        if(StrUtil.isNotEmpty(shopJson)){

            List<ShopType> typeList = JSONUtil.toList(shopJson, ShopType.class);
            return Result.ok(typeList);
        }
        //不存在 查询数据库
        List<ShopType> typeList = query().list();
        if(typeList == null || typeList.isEmpty()){
            return Result.fail("商品类型不存在");
        }

        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);

    }
}
