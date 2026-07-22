package com.scott.payment.merchant.dto.system;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDictDTOs
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台只读字典 DTO 集合，位于 service-merchant 接口传输层，用于页面筛选项和状态展示，不提供字典维护能力。
 * @status : create
 */
public final class MerchantDictDTOs {

    private MerchantDictDTOs() {
    }

    /**
     * 商户后台字典项查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DictDataQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 字典类型编码，必填时精确查询。
         */
        private String dictType;

        /**
         * 字典标签，支持右模糊查询。
         */
        private String dictLabel;

        /**
         * 字典值，支持精确查询。
         */
        private String dictValue;

        /**
         * 父级字典值，可为空。
         */
        private String parentValue;

        /**
         * 语言区域，例如 zh-CN、en-US。
         */
        private String locale;

        /**
         * 字典状态：0 停用，1 启用；商户页面默认只取启用项。
         */
        private Integer status;
    }

    /**
     * 商户后台字典项响应。
     */
    @Data
    public static class DictDataResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 字典项主键。
         */
        private Long id;

        /**
         * 字典类型编码。
         */
        private String dictType;

        /**
         * 展示标签。
         */
        private String dictLabel;

        /**
         * 业务值。
         */
        private String dictValue;

        /**
         * 父级字典值。
         */
        private String parentValue;

        /**
         * 语言区域。
         */
        private String locale;

        /**
         * 排序。
         */
        private Integer dictSort;

        /**
         * 前端状态标签样式。
         */
        private String listClass;

        /**
         * 扩展 JSON。
         */
        private String extraJson;

        /**
         * 是否默认项。
         */
        private Integer isDefault;

        /**
         * 状态：0 停用，1 启用。
         */
        private Integer status;
    }
}
