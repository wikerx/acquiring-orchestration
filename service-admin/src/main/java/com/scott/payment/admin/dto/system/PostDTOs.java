package com.scott.payment.admin.dto.system;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统岗位接口使用的请求与响应模型。
 */
public final class PostDTOs {

    /**
     * 阻止仅承载嵌套 DTO 的类型被实例化。
     */
    private PostDTOs() {
    }

    /**
     * 新增或编辑岗位的请求。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 岗位编码。
         */
        private String postCode;

        /**
         * 岗位名称。
         */
        private String postName;

        /**
         * 显示排序号。
         */
        private Integer sortNo;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;

        /**
         * 岗位备注。
         */
        private String remark;
    }

    /**
     * 岗位资料响应。
     */
    @Data
    public static class Response implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 岗位主键。
         */
        private Long id;

        /**
         * 所属系统应用主键。
         */
        private Long appId;

        /**
         * 岗位编码。
         */
        private String postCode;

        /**
         * 岗位名称。
         */
        private String postName;

        /**
         * 显示排序号。
         */
        private Integer sortNo;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;

        /**
         * 岗位备注。
         */
        private String remark;

        /**
         * 逻辑删除标记，保留该字段以兼容既有管理端响应。
         */
        private Long deleted;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createdAt;

        /**
         * 记录最后更新时间。
         */
        private LocalDateTime updatedAt;
    }
}
