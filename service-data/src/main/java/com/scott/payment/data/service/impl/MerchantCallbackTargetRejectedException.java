package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackTargetRejectedException
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 商户通知目标被环境安全策略拒绝的内部异常，用于将永久配置错误与可重试网络故障分离。
 * @status : create
 */
public class MerchantCallbackTargetRejectedException extends ServiceException {

    private static final long serialVersionUID = 1L;

    /** 创建不暴露 DNS 或内网地址细节的策略拒绝异常。 */
    public MerchantCallbackTargetRejectedException() {
        super(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED);
    }

    /** 创建保留异常因果链但不回显目标细节的策略拒绝异常。 */
    public MerchantCallbackTargetRejectedException(Throwable cause) {
        super(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED, cause);
    }
}
