package com.miaoshaproject.service;

import com.miaoshaproject.service.model.PromoModel;

public interface PromoService {

    //根据itemid获取即将进行的或者正在进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);

    //活动发布
    void publishPromo(Integer promoId);

    //生成秒杀的令牌。以promoId为级别开的，用户需要依靠promoId来完成对应的令牌生成的动作。
    String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId);
}
