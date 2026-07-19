package com.scott.payment.merchant.service;

import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTemplateEmailService
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户模板邮件服务，位于 service-merchant 服务层；复用管理系统邮件模板配置发送商户安全通知并落发送记录。
 * @status : create
 */
public interface MerchantTemplateEmailService {

    /**
     * 按模板发送商户邮件。
     *
     * @param request 邮件发送请求
     */
    void sendByTemplate(MerchantEmailSendCommand request);

    /**
     * 商户模板邮件发送命令。
     */
    record MerchantEmailSendCommand(String appCode,
                                    String merchantId,
                                    String merchantNo,
                                    String merchantName,
                                    String templateCode,
                                    String sceneCode,
                                    String locale,
                                    List<String> toEmails,
                                    Map<String, Object> variables,
                                    String bizType,
                                    String bizNo) {
    }
}
