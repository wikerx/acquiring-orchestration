package com.scott.payment.data.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationReconcileCommandDTO
 * @date : 2026-08-06 12:44
 * @email : scott_x@163.com
 * @description : service-data 低频商户通知 MQ 对账命令，空季度列表表示扫描全部已发布季度
 * @status : create
 */
@Data
public class MerchantNotificationReconcileCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 可选季度定位时间；为空时由服务端读取全部已验证物理节点。 */
    private List<LocalDateTime> transactionDateTimes = Collections.emptyList();

    /** 每季度最多可靠入队数量。 */
    private Integer limit;
}
