package com.miaoshaproject.controller;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EmBusinessError;
import com.miaoshaproject.response.CommonReturnType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class BaseController {

    public static final String CONTENT_TYPE_FORMED="application/x-www-form-urlencoded";

    //定义ExceptionHandler解决未被controller层吸收的exception
//    @ExceptionHandler(Exception.class)
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody
//    public Object handlerException(HttpServletRequest request, Exception ex)
//    {
//        CommonReturnType commonReturnType = new CommonReturnType();
//        Map<String, Object> responseData = new HashMap<>();
//        if(ex instanceof BusinessException)
//        {
//            BusinessException businessException = (BusinessException)ex;
//            commonReturnType.setStatus("fail");
//            responseData.put("errCode", businessException.getErrCode());
//            responseData.put("errMsg", businessException.getErrMsg());
//        }else{
//            commonReturnType.setStatus("fail");
//            responseData.put("errCode", EmBusinessError.UNKONWN_ERROR.getErrCode());
//            responseData.put("errMsg", ex.getMessage());
//
//        }
//        commonReturnType.setData(responseData);
//        return commonReturnType;
//    }
}
