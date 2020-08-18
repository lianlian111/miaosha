package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.OrderDOMapper;
import com.miaoshaproject.dao.SequenceDOMapper;
import com.miaoshaproject.dao.StockLogDOMapper;
import com.miaoshaproject.dataobject.OrderDO;
import com.miaoshaproject.dataobject.SequenceDO;
import com.miaoshaproject.dataobject.StockLogDO;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EmBusinessError;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.OrderService;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.OrderModel;
import com.miaoshaproject.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Override
    @Transactional
    //事务状态下操作，失败则回滚
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException {
        //1、校验下单状态下，下单的商品是否存在，用户是否合法，购买数量是否正确
        //直接到数据库中去查询
//        ItemModel itemModel = itemService.getItemById(itemId);
        //减少对数据库的依赖
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null)
        {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }

//        UserModel userModel = userService.getUserByIdInCache(userId);
//        if(userModel == null)
//        {
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户信息不存在");
//        }
        if(amount<=0 || amount>99)
        {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不正确");
        }

//        //校验活动信息
//        if(promoId!=null)
//        {
//            //(1)校验对应活动是否存在这个适应商品
//            if(promoId.intValue() != itemModel.getPromoModel().getId())
//            {
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "活动信息不正确");
//            }else if(itemModel.getPromoModel().getStatus().intValue() != 2)
//            {
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "活动信息还未开始");
//            }
//        }

        //2、落单减库存(还有一种支付减库存)，此处剪的是redis中商品的库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if(!result)
        {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //3、订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null)
        {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
            orderModel.setOrderPrice(itemModel.getPromoModel().getPromoItemPrice().multiply(new BigDecimal(amount)));
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
            orderModel.setOrderPrice(itemModel.getPrice().multiply(new BigDecimal(amount)));
        }
        orderModel.setPromoId(promoId);

        //生成交易流水号，也就是订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //加上商品的销量，加到商品的mysql上。
        itemService.increaseSales(itemId,amount);

        //设置库存流水状态为成功.这里select和update的操作并不会影响数据库的性能，因为这是针对
        //每个订单的操作，并不是itemId级别，每一个交易上的行锁都是单独的行锁，因此单独的行锁对数据库
        // 的压力是非常小的，因为没有一个锁并发竞争的状态。扣减库存的操作是针对itemId级别的，
        // 所有减库存的操作都是加在这个itemId的行锁上面。
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO == null)
        {
            throw new BusinessException(EmBusinessError.UNKONWN_ERROR);
        }
        stockLogDO.setStatus(2);//2表示下单成功
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

        //spring transactional标签只有等整个方法返回之后才会去commit，若commit时由于网络消息
        //或者磁盘满了发生失败，那么缓存库存还是被扣减，因此spring transaction标签提供给我们提交
        //事务之后再去执行某个方法，实现如下：
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//            //该方法会在最近的transaction被提交之后执行
//            @Override
//            public void afterCommit() {
//                //异步更新库存
//                boolean mqResult = itemService.asyncDecreaseStock(itemId, amount);
//                //仍然有一个问题，当消息发送失败那么数据库中的库存不能扣减，所以必须发送成功
//                if(!mqResult)
//                {
//                    itemService.increaseStock(itemId, amount);
//                    throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
//                }
//            }
//        });

//        //异步更新库存
//        boolean mqResult = itemService.asyncDecreaseStock(itemId, amount);
//        if(!mqResult)
//        {
//            itemService.increaseStock(itemId, amount);
//            throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
//        }

        //返回前端
        return orderModel;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected String generateOrderNo()
    {
        StringBuilder stringBuilder = new StringBuilder();
        //订单号有16位
        //前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);

        //中间6位为自增序列
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue()+sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String sequenceStr = String.valueOf(sequence);
        for(int i=0; i<6-sequenceStr.length(); i++)
        {
            stringBuilder.append(0);
        }
        stringBuilder.append(sequenceStr);

        //最后2位为分库分表位，暂时写死
        stringBuilder.append("00");
        return stringBuilder.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel)
    {
        if(orderModel == null)
        {
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
