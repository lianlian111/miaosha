package com.miaoshaproject.service;

public interface CacheService {

    //存方法
    void setCommonCache(String key, Object value);

    //取方法
    Object getFromCommentCache(String key);
}
