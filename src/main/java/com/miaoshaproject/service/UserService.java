package com.miaoshaproject.service;

import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.model.UserModel;

public interface UserService {

    //通过ID获取用户对象
    UserModel getUserById(Integer id);

    //注册用户
    void register(UserModel userModel) throws BusinessException;

    /**
     * 检验用户登陆是否合法
     * @param telPhone:用户注册的手机号
     * @param encrptPassword：用户加密后的密码
     * @throws BusinessException
     */
    UserModel validateLogin(String telPhone, String encrptPassword) throws BusinessException;

    //通过缓存获取用户对象
    UserModel getUserByIdInCache(Integer id);
}
