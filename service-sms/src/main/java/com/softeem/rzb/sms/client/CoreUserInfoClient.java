package com.softeem.rzb.sms.client;

import com.softeem.rzb.sms.client.fallback.CoreUserInfoClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "service-core", fallback = CoreUserInfoClientFallback.class)
public interface CoreUserInfoClient {

    /**
     * 校验是否手机号存在,但此服务不会自己去数据库中称查询,但会远程调用service-core 中的checkmobile方法
     * @param mobile
     * @return
     */
    @GetMapping("/api/core/userInfo/checkMobile/{mobile}")
    boolean checkMobile(@PathVariable String mobile);
}