package com.scott.payment.admin.dto.system;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 系统文件模板中心请求与响应模型。 */
public final class FileTemplateDTOs {

    private FileTemplateDTOs() {
    }

    /** 分页检索条件。 */
    @Data
    public static class Query {
        @Min(1)
        private long pageNo = 1;
        @Min(1)
        @Max(100)
        private long pageSize = 20;
        @Size(max = 128)
        private String keyword;
        @Pattern(regexp = "ADMIN|MERCHANT|SHARED")
        private String ownerSystem;
        @Min(0)
        @Max(1)
        private Integer status;
    }

    /** 新增模板定义。文件名不包含扩展名。 */
    @Data
    public static class CreateRequest {
        /** 仅系统内置模板初始化使用；普通新增为空时由后端生成。 */
        @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
        private String templateCode;
        @NotBlank
        @Pattern(regexp = "ADMIN|MERCHANT|SHARED")
        private String ownerSystem;
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
        private String moduleCode;
        @NotBlank
        @Size(max = 128)
        private String nameZh;
        @NotBlank
        @Size(max = 128)
        private String nameEn;
        @NotBlank
        @Pattern(regexp = "xlsx|csv|pdf|docx|zip")
        private String fileExtension;
        @Size(max = 128)
        private String adminPermissionCode;
        @Size(max = 128)
        private String merchantPermissionCode;
        @Size(max = 500)
        private String remark;
    }

    /** 模板新增/编辑表单的受控选项。 */
    @Data
    public static class FormOptions {
        private List<ModuleOption> modules = new ArrayList<>();
        private List<PermissionOption> adminPermissions = new ArrayList<>();
        private List<PermissionOption> merchantPermissions = new ArrayList<>();
    }

    /** 后端维护的业务模块选项。 */
    @Data
    public static class ModuleOption {
        private String code;
        private String nameZh;
        private String nameEn;
        private List<String> ownerSystems = new ArrayList<>();
    }

    /** 当前启用的业务权限选项。 */
    @Data
    public static class PermissionOption {
        private String code;
        private String name;
    }

    /** 修改可变元数据；模板编码和文件扩展名创建后不可修改。 */
    @Data
    public static class UpdateRequest {
        @NotBlank
        @Pattern(regexp = "ADMIN|MERCHANT|SHARED")
        private String ownerSystem;
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
        private String moduleCode;
        @NotBlank
        @Size(max = 128)
        private String nameZh;
        @NotBlank
        @Size(max = 128)
        private String nameEn;
        @Size(max = 128)
        private String adminPermissionCode;
        @Size(max = 128)
        private String merchantPermissionCode;
        @NotNull
        @Min(0)
        @Max(1)
        private Integer status;
        @Size(max = 500)
        private String remark;
        @NotNull
        @Min(0)
        private Long expectedVersion;
    }

    /** 发布或回滚命令。 */
    @Data
    public static class PublishRequest {
        @NotNull
        @Min(0)
        private Long expectedTemplateVersion;
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
        private String requestId;
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    /** 模板定义摘要。 */
    @Data
    public static class Summary {
        private Long id;
        private String templateCode;
        private String ownerSystem;
        private String moduleCode;
        private String nameZh;
        private String nameEn;
        private String fileExtension;
        private String adminPermissionCode;
        private String merchantPermissionCode;
        private Integer status;
        private Long currentVersionId;
        private Integer currentVersionNo;
        private Long version;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /** 模板详情与版本列表。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Detail extends Summary {
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private List<Version> versions = new ArrayList<>();
    }

    /** 不暴露对象存储定位的版本元数据。 */
    @Data
    public static class Version {
        private Long id;
        private Integer versionNo;
        private String schemaVersion;
        private String requestId;
        private String releaseNote;
        private String publishStatus;
        private String publishedBy;
        private LocalDateTime publishedTime;
        private String createBy;
        private LocalDateTime createTime;
        private List<Asset> assets = new ArrayList<>();
    }

    /** 可向管理端展示的安全文件元数据。 */
    @Data
    public static class Asset {
        private Long id;
        private String locale;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String sha256;
    }

    /** 受控下载结果；只在服务端内部传递。 */
    public record Download(String filename, String contentType, byte[] content) {
    }
}
