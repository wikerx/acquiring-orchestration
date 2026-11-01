package com.scott.payment.admin.dto.system;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知公告接口使用的请求与响应模型。
 */
public final class NoticeDTOs {

    /**
     * 阻止仅承载嵌套 DTO 的类型被实例化。
     */
    private NoticeDTOs() {
    }

    /**
     * 批量删除通知公告的请求。
     *
     * @param ids 通知公告主键列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchDeleteRequest(List<Long> ids) implements Serializable {

        private static final long serialVersionUID = 1L;
    }

    /**
     * 新增或编辑通知公告的请求。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 通知标题。
         */
        private String noticeTitle;

        /**
         * 通知类型，1 表示通知，2 表示公告。
         */
        private String noticeType;

        /**
         * 通知正文。
         */
        private String noticeContent;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;
    }

    /**
     * 通知公告响应。
     */
    @Data
    public static class Response implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 通知公告主键。
         */
        private Long id;

        /**
         * 通知标题。
         */
        private String noticeTitle;

        /**
         * 通知类型，1 表示通知，2 表示公告。
         */
        private String noticeType;

        /**
         * 通知正文。
         */
        private String noticeContent;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;

        /**
         * 创建人账号或名称。
         */
        private String createBy;

        /**
         * 记录最后更新时间。
         */
        private LocalDateTime updatedAt;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createdAt;

        /**
         * 逻辑删除标记，保留该字段以兼容既有管理端响应。
         */
        private Long deleted;
    }
}
