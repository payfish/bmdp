package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryList() {

        String key = RedisConstants.CACHE_SHOP_TYPE;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(jsonStr)) {
            return JSONUtil.toList(jsonStr, ShopType.class);
        }

        List<ShopType> list = query().list();

        if (list != null && list.size() > 0) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list));
        }
        return list;
    }
}
