package com.miaoshaproject.service;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.model.OrderModel;

public interface OrderService {

    //1、通过前端url传过来秒杀活动ID，然后下单接口内校验对应ID是否属于对应商品并且活动已经开始
    //2、直接在下单接口内判断对应的商品是否存在秒杀活动，若存在进行中的秒杀活动则以秒杀价格下单
    //推荐使用第一种方法
    OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException;
}
