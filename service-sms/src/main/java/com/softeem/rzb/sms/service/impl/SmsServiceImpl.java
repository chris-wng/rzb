package com.softeem.rzb.sms.service.impl;

import com.softeem.common.exception.Assert;
import com.softeem.common.exception.BusinessException;
import com.softeem.common.result.ResponseEnum;
import com.softeem.rzb.sms.service.SmsService;
import com.softeem.rzb.sms.util.SMSUtils;
import com.softeem.rzb.sms.util.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.rmi.ServerException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Chris
 */
@Service
@Slf4j
public class SmsServiceImpl implements SmsService {

    /**
     * 发短信
     * @param mobile 手机号码
     * @param templateCode 短信模板
     * @param param 随机数验证码
     */
    @Override
    public void send(String mobile, String templateCode, Map<String,Object> param) {
        String code = (String)param.get("code");
        SMSUtils.sendShortMessage(templateCode,mobile,code);
    }
}