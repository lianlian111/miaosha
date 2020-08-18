package com.miaoshaproject.controller;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EmBusinessError;
import com.miaoshaproject.response.CommonReturnType;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

//这是一个增强的 Controller。使用这个 Controller ，可以实现三个方面的功能：
//  全局异常处理
//  全局数据绑定
//  全局数据预处理

//@responseBody注解的作用是将controller的方法返回的对象通过适当的转换器转换为指定的格式之后，写入到response对象的body区，
// 通常用来返回JSON数据或者是XML数据。
//注意：在使用此注解之后不会再走视图处理器，而是直接将数据写入到输入流中，他的效果等同于通过response对象输出指定格式的数据。

//@ExceptionHandler(Exception.class) 统一异常处理器，配置处理Exception类型的异常。
@ControllerAdvice
public class GlobalExceptionHandler {

    //定义ExceptionHandler解决未被controller层吸收的exception
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CommonReturnType doError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                                    Exception ex)
    {
        ex.printStackTrace();
        Map<String, Object> responseData = new HashMap<>();
        CommonReturnType commonReturnType = new CommonReturnType();
        if(ex instanceof BusinessException)
        {
            BusinessException businessException = (BusinessException)ex;
            responseData.put("errCode", businessException.getErrCode());
            responseData.put("errMsg", businessException.getErrMsg());
        }else if(ex instanceof ServletRequestBindingException)
        {
            //针对405问题
            responseData.put("errCode", EmBusinessError.UNKONWN_ERROR.getErrCode());
            responseData.put("errMsg", "url路由绑定问题");
        }else if(ex instanceof NoHandlerFoundException)
        {
            //针对404问题
            responseData.put("errCode", EmBusinessError.UNKONWN_ERROR.getErrCode());
            responseData.put("errMsg", "没有找到对应的访问路径");
        }else{
            responseData.put("errCode", EmBusinessError.UNKONWN_ERROR.getErrCode());
            responseData.put("errMsg", EmBusinessError.UNKONWN_ERROR.getErrMsg());
        }
        commonReturnType.setData(responseData);
        return commonReturnType;
    }
}
