package com.miaoshaproject.error;

public enum EmBusinessError implements CommonError {

    //全局错误码
    //通用错误类型10001
    PARAMETER_VALIDATION_ERROR(10001, "参数不合法"),

    UNKONWN_ERROR(10002, "未知错误"),

    //20000开头为用户信息相关错误定义
    USER_NOT_EXIST(20001, "用户不存在"),
    USER_LOGIN_FAIL(20002, "电话号码或者密码错误"),
    USER_NOT_LOGIN(20003,"用户还未登陆"),

    //30000开头为订单信息相关错误定义
    STOCK_NOT_ENOUGH(30001, "库存不足"),
    MQ_SEND_FAIL(30002,"库存异步消息失败"),
    RATELIMIT(30003,"活动太火爆，请稍后再试"),
    ;

    private EmBusinessError(int errCode, String errMsg)
    {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    //枚举可以拥有属性
    private int errCode;
    private String errMsg;

    @Override
    public int getErrCode() {
        return errCode;
    }

    @Override
    public String getErrMsg() {
        return errMsg;
    }

    //对于通用错误类型，可以定制不同的errMsg
    @Override
    public CommonError setErrMsg(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }
}
