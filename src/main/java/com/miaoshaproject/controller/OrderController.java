package com.miaoshaproject.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EmBusinessError;
import com.miaoshaproject.mq.MqProducer;
import com.miaoshaproject.response.CommonReturnType;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.OrderService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.model.OrderModel;
import com.miaoshaproject.service.model.UserModel;
import com.miaoshaproject.util.CodeUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.MQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.RenderedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.*;


@Controller("order")
@RequestMapping("/order")
//解决跨域问题，默认的@CrossOrigin不能做到对session共享,需要设置allowCredentials ="true", allowedHeaders = "*"
@CrossOrigin(allowCredentials ="true", allowedHeaders = "*")
public class OrderController extends BaseController {

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    private ExecutorService executorService;

    //使用Guava RateLimiter限流以及源码解析：https://www.jianshu.com/p/5d4fe4b2a726
    private RateLimiter orderCreateRateLimiter;

    @PostConstruct
    public void init()
    {
        executorService = Executors.newFixedThreadPool(20);
        orderCreateRateLimiter = RateLimiter.create(300);
    }

    //生成验证码
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.GET})
    @ResponseBody
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        //根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token))
        {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能生成验证码");
        }
        //获取用户的登陆信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null)
        {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能生成验证码");
        }
        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(), map.get("code"));
        redisTemplate.expire("verify_code_"+userModel.getId(), 10, TimeUnit.MINUTES);
        ImageIO.write((RenderedImage)map.get("codePic"), "jpg", response.getOutputStream());
    }

    //生成秒杀令牌
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "promoId")Integer promoId,
                                          @RequestParam(name = "verifyCode")String verifyCode) throws BusinessException {
        //根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token))
        {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户登陆信息不存在，不能下单");
        }
        //获取用户的登陆信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null)
        {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户登陆信息不存在，不能下单");
        }
        //通过verifycode验证验证码的有效性
        String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_"+userModel.getId());
        if(StringUtils.isEmpty(redisVerifyCode))
        {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "请求非法");
        }
        if(!redisVerifyCode.equalsIgnoreCase(verifyCode))
        {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "请求非法,验证码错误");
        }
        //获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
        if(promoToken == null)
        {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        }
        //返回对应的结果
        return CommonReturnType.creat(promoToken);
    }

    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "amount")Integer amount,
                                        @RequestParam(name = "promoId",required = false)Integer promoId,
                                        @RequestParam(name = "promoToken",required = false)String promoToken) throws BusinessException {
        //尝试获取限流器的许可，如果不能直接获取到，直接返回
        if(!orderCreateRateLimiter.tryAcquire())
        {
            throw new BusinessException(EmBusinessError.RATELIMIT);

        }

        //1.1获取用户的登陆信息
//        HttpSession httpSession = httpServletRequest.getSession();
//        Boolean isLogin = (Boolean) httpSession.getAttribute("IS_LOGIN");
//        if(isLogin == null || !isLogin.booleanValue())
//        {
//            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户登陆信息不存在，不能下单");
//        }
//        //获取登陆用户信息
//        UserModel userModel = (UserModel)httpServletRequest.getSession().getAttribute("LOGIN_USER");

        //1.2获取用户的登陆信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token))
        {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户登陆信息不存在，不能下单");
        }
        //获取用户的登陆信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null)
        {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户登陆信息不存在，不能下单");
        }

        //校验秒杀令牌是否正确
        if(promoToken != null)
        {
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_"+promoId+"_userId_"+userModel.getId()+"_itemId_"+itemId);
            if(inRedisPromoToken == null)
            {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }

            if(!StringUtils.equals(promoToken, inRedisPromoToken))
            {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
        }

//        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId,promoId, amount);

        //判断库存是否已售罄，若对应的售罄key存在，则直接返回下单失败
        if (redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //同步线程池的submit方法
        //拥塞窗口为20的等待队列，用来队列化泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {

            public Object call() throws Exception
            {
                //加入库存流水init状态
                String stockLogId = itemService.initStockLog(itemId,amount);

                //再去完成对应的下单事务型消息机制
                if(!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId))
                {
                    throw new BusinessException(EmBusinessError.UNKONWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKONWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKONWN_ERROR);
        }

        return CommonReturnType.creat(null);
    }
}
