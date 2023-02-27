package com.softeem.rzb.core.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.softeem.common.exception.Assert;
import com.softeem.common.result.ResponseEnum;
import com.softeem.rzb.base.dto.SmsDTO;
import com.softeem.rzb.core.enums.TransTypeEnum;
import com.softeem.rzb.core.hfb.FormHelper;
import com.softeem.rzb.core.hfb.HfbConst;
import com.softeem.rzb.core.hfb.RequestHelper;
import com.softeem.rzb.core.mapper.UserInfoMapper;
import com.softeem.rzb.core.pojo.bo.TransFlowBO;
import com.softeem.rzb.core.pojo.entity.UserAccount;
import com.softeem.rzb.core.mapper.UserAccountMapper;
import com.softeem.rzb.core.pojo.entity.UserInfo;
import com.softeem.rzb.core.service.TransFlowService;
import com.softeem.rzb.core.service.UserAccountService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.softeem.rzb.core.service.UserBindService;
import com.softeem.rzb.core.service.UserInfoService;
import com.softeem.rzb.core.util.LendNoUtils;
import com.softeem.rzb.rabbitutil.constant.MQConst;
import com.softeem.rzb.rabbitutil.service.MQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 用户账户 服务实现类
 * </p>
 *
 * @author chris
 * @since 2023-02-08
 */
@Service
@Slf4j
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private TransFlowService transFlowService;


    @Resource
    private UserBindService userBindService;

    @Resource
    private UserAccountService userAccountService;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MQService mqService;

    @Override
    public String commitWithdraw(BigDecimal fetchAmt, Long userId) {

        //账户可用余额充足：当前用户的余额 >= 当前用户的提现金额
        BigDecimal amount = userAccountService.getAccount(userId);//获取当前用户的账户余额
        Assert.isTrue(amount.doubleValue() >= fetchAmt.doubleValue(),
            ResponseEnum.NOT_SUFFICIENT_FUNDS_ERROR);

        String bindCode = userBindService.getBindCodeByUserId(userId);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentBillNo", LendNoUtils.getWithdrawNo());
        paramMap.put("bindCode", bindCode);
        paramMap.put("fetchAmt", fetchAmt);
        paramMap.put("feeAmt", new BigDecimal(0));
        paramMap.put("notifyUrl", HfbConst.WITHDRAW_NOTIFY_URL);
        paramMap.put("returnUrl", HfbConst.WITHDRAW_RETURN_URL);
        paramMap.put("timestamp", RequestHelper.getTimestamp());
        String sign = RequestHelper.getSign(paramMap);
        paramMap.put("sign", sign);

        //构建自动提交表单
        String formStr = FormHelper.buildForm(HfbConst.WITHDRAW_URL, paramMap);
        return formStr;
    }

    @Override
    public String commitCharge(BigDecimal chargeAmt, Long userId) {

        UserInfo userInfo = userInfoMapper.selectById(userId);
        String bindCode = userInfo.getBindCode();
        //判断账户绑定状态
        Assert.notEmpty(bindCode, ResponseEnum.USER_NO_BIND_ERROR);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("agentId", HfbConst.AGENT_ID);
        paramMap.put("agentBillNo", LendNoUtils.getNo());
        paramMap.put("bindCode", bindCode);
        paramMap.put("chargeAmt", chargeAmt);
        paramMap.put("feeAmt", new BigDecimal("0"));
        paramMap.put("notifyUrl", HfbConst.RECHARGE_NOTIFY_URL);//检查常量是否正确
        paramMap.put("returnUrl", HfbConst.RECHARGE_RETURN_URL);
        paramMap.put("timestamp", RequestHelper.getTimestamp());
        String sign = RequestHelper.getSign(paramMap);
        paramMap.put("sign", sign);

        //构建充值自动提交表单
        String formStr = FormHelper.buildForm(HfbConst.RECHARGE_URL, paramMap);
        return formStr;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String notify(Map<String, Object> paramMap) {

        log.info("充值成功：" + JSONObject.toJSONString(paramMap));
        //判断交易流水是否存在
        String agentBillNo = (String) paramMap.get("agentBillNo"); //商户充值订单号
        boolean isSave = transFlowService.isSaveTransFlow(agentBillNo);
        if (isSave) {
            log.warn("幂等性返回");
            return "success";
        }
        String bindCode = (String) paramMap.get("bindCode"); //充值人绑定协议号
        String chargeAmt = (String) paramMap.get("chargeAmt"); //充值金额

        //优化
        baseMapper.updateAccount(bindCode, new BigDecimal(chargeAmt), new BigDecimal(0));

        //TODO 增加交易流水
        // agentBillNo = (String) paramMap.get("agentBillNo"); //商户充值订单号
        TransFlowBO transFlowBO = new TransFlowBO(
            agentBillNo,
            bindCode,
            new BigDecimal(chargeAmt),
            TransTypeEnum.RECHARGE,
            "充值");
        transFlowService.saveTransFlow(transFlowBO);
        //发消息
        log.info("发消息");
        String mobile = userInfoService.getMobileByBindCode(bindCode);
        SmsDTO smsDTO = new SmsDTO();
        smsDTO.setMobile(mobile);
        smsDTO.setMessage("6666");
        mqService.sendMessage(MQConst.EXCHANGE_TOPIC_SMS, MQConst.ROUTING_SMS_ITEM, smsDTO);
        return "success";
    }

    @Override
    public BigDecimal getAccount(Long userId) {
        //根据userId查找用户账户
        QueryWrapper<UserAccount> userAccountQueryWrapper = new QueryWrapper<>();
        userAccountQueryWrapper.eq("user_id", userId);
        UserAccount userAccount = baseMapper.selectOne(userAccountQueryWrapper);

        BigDecimal amount = userAccount.getAmount();
        return amount;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void notifyWithdraw(Map<String, Object> paramMap) {

        log.info("提现成功");
        String agentBillNo = (String) paramMap.get("agentBillNo");
        boolean result = transFlowService.isSaveTransFlow(agentBillNo);
        if (result) {
            log.warn("幂等性返回");
            return;
        }

        String bindCode = (String) paramMap.get("bindCode");
        String fetchAmt = (String) paramMap.get("fetchAmt");

        //根据用户账户修改账户金额
        baseMapper.updateAccount(bindCode, new BigDecimal("-" + fetchAmt), new BigDecimal(0));

        //增加交易流水
        TransFlowBO transFlowBO = new TransFlowBO(
            agentBillNo,
            bindCode,
            new BigDecimal(fetchAmt),
            TransTypeEnum.WITHDRAW,
            "提现");
        transFlowService.saveTransFlow(transFlowBO);
    }
}
