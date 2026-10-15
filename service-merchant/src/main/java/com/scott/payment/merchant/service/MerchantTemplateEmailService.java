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
    MerchantEmailSendResult sendByTemplate(MerchantEmailSendCommand request);

    /**
     * 完整准备并持久化一封抑制投递的模板邮件。
     * <p>
     * 用于匿名安全流程的虚拟请求：模板选择、发件账户选择、渲染、加密、发送记录和
     * Outbox 写入均与真实请求保持一致，但记录直接进入 CANCELLED 终态且没有收件人，
     * 消费者不得执行 SMTP 投递。
     * </p>
     *
     * @param request 邮件准备请求
     * @return 终态邮件记录
     */
    MerchantEmailSendResult prepareSuppressedByTemplate(MerchantEmailSendCommand request);

    /**
     * 邮件准备结果。状态为 CLOSED 或 CANCELLED 时，调用方必须按准备失败处理。
     *
     * @param recordId  邮件发送记录主键
     * @param sendStatus 邮件可靠投递状态
     */
    record MerchantEmailSendResult(Long recordId, Integer sendStatus) {
    }

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
