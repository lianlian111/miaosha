package com.miaoshaproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SpringSessionConfig {

    public SpringSessionConfig()
    {
    }

    //因为服务端返回给客户端的set-cookie中带有samesite=lax，这就是问题的根源，它表示不能携带cookie进行跨域post访问，
    // 然而我们是需要携带cookie的
    //解决办法：https://blog.csdn.net/qq_37060233/article/details/86595102
    @Bean
    public CookieSerializer httpSessionIdResolver()
    {
        DefaultCookieSerializer defaultCookieSerializer = new DefaultCookieSerializer();
        //取消samesite
        defaultCookieSerializer.setSameSite(null);
        return defaultCookieSerializer;
    }
}
