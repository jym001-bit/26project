package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long followUsedId, Boolean isFollow) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();

        //判断当前关注还是取关
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUsedId);
            save(follow);
        }else{
            //取关
            remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id",followUsedId)
                    .eq("user_id", userId));
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUsedId) {
        Long userID = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userID).eq("follow_user_id", followUsedId).count();
        return Result.ok(count > 0);
    }
}
