package com.scott.payment.admin.dto.system;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统部门维护接口使用的请求与详情响应模型。
 */
public final class DeptDTOs {

    /**
     * 阻止仅承载嵌套 DTO 的类型被实例化。
     */
    private DeptDTOs() {
    }

    /**
     * 新增或编辑部门的请求。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 父部门主键，根部门使用 0。
         */
        private Long parentId;

        /**
         * 部门名称。
         */
        private String deptName;

        /**
         * 显示排序号。
         */
        private Integer sortNo;

        /**
         * 部门负责人。
         */
        private String leader;

        /**
         * 联系电话。
         */
        private String phone;

        /**
         * 联系邮箱。
         */
        private String email;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;
    }

    /**
     * 部门详情和写操作结果响应。
     */
    @Data
    public static class Response implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 部门主键。
         */
        private Long id;

        /**
         * 所属系统应用主键。
         */
        private Long appId;

        /**
         * 父部门主键，根部门使用 0。
         */
        private Long parentId;

        /**
         * 部门名称。
         */
        private String deptName;

        /**
         * 显示排序号。
         */
        private Integer sortNo;

        /**
         * 部门负责人。
         */
        private String leader;

        /**
         * 联系电话。
         */
        private String phone;

        /**
         * 联系邮箱。
         */
        private String email;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;

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
