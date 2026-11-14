package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端系统交易加黑的交易事实查询命令。
 *
 * <p>该命令只接受交易定位条件，不接受任何名单哈希或脱敏值。所有敏感事实均由
 * service-payment 从交易快照中读取，并且仅通过已签名的内部接口返回。</p>
 */
@Data
public class TradeBlackTransactionResolveCommandDTO {

    /** 单次内部调用上限，管理端的大文件会分批调用。 */
    public static final int MAX_BATCH_SIZE = 500;

    /** 待定位的交易引用。 */
    @Valid
    @NotEmpty(message = "交易定位条件不能为空")
    @Size(max = MAX_BATCH_SIZE, message = "单次最多查询500笔交易")
    private List<Reference> references;

    /** 单笔交易定位引用。 */
    @Data
    public static class Reference {

        /** 调用方行号，用于原样关联批量结果。 */
        @NotNull(message = "交易行号不能为空")
        private Integer rowNo;

        /** 标识类型：MERCHANT_ORDER_NO、SYSTEM_ORDER_NO。 */
        @NotNull(message = "交易标识类型不能为空")
        private IdentifierType identifierType;

        /** 商户订单号查询时必填；系统订单号查询时忽略。 */
        private String lookupMerchantId;

        /** 商户订单号或系统订单号。 */
        @jakarta.validation.constraints.NotBlank(message = "订单号不能为空")
        private String orderNo;
    }

    /** 受支持的交易定位方式。 */
    public enum IdentifierType {
        MERCHANT_ORDER_NO,
        SYSTEM_ORDER_NO
    }
}
