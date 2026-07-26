package com.scott.payment.merchant.dto.system;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemDTOs
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : MerchantSystemDTOs 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public final class MerchantSystemDTOs {

    private MerchantSystemDTOs() {
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : IdsRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : IdsRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class IdsRequest {
        /**
         * ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> ids = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : StatusRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : StatusRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class StatusRequest {
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : DeptQueryRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class DeptQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : DeptSaveRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class DeptSaveRequest {
        /**
         * parent Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long parentId = 0L;
        /**
         * dept Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String deptCode;
        /**
         * dept Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String deptName;
        /**
         * leader Account Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long leaderAccountId;
        /**
         * phone 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * sort No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer sortNo = 0;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status = 1;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : DeptDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : DeptDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class DeptDTO {
        /**
         * dept Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long deptId;
        /**
         * parent Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long parentId;
        /**
         * dept Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String deptCode;
        /**
         * dept Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String deptName;
        /**
         * leader Account Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long leaderAccountId;
        /**
         * phone 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String phone;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * sort No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer sortNo;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * created At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * updated At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime updatedAt;
        /**
         * children 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<DeptDTO> children = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : PostQueryRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class PostQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : PostSaveRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class PostSaveRequest {
        /**
         * post Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String postCode;
        /**
         * post Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String postName;
        /**
         * sort No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer sortNo = 0;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status = 1;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PostDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : PostDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class PostDTO {
        /**
         * post Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long postId;
        /**
         * post Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String postCode;
        /**
         * post Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String postName;
        /**
         * sort No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer sortNo;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
        /**
         * 商户管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * created At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * updated At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime updatedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountQueryRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountQueryRequest extends PageRequest {
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * role Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long roleId;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountBaseSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountBaseSaveRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountBaseSaveRequest {
        /**
         * login Account 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String loginAccount;
        /**
         * 商户管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String password;
        /**
         * real Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String realName;
        /**
         * mobile 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status = 1;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountSaveRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountSaveRequest extends AccountBaseSaveRequest {
        /**
         * role Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> roleIds = Collections.emptyList();
        /**
         * dept Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> deptIds = Collections.emptyList();
        /**
         * post Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> postIds = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountResetPasswordRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountResetPasswordRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountResetPasswordRequest {
        /**
         * 商户员工新登录密码。接口层只接收明文，服务层必须立即哈希后落库，禁止写入日志。
         */
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
        private String password;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountDTO {
        /**
         * account Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long accountId;
        /**
         * user Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long userId;
        /**
         * merchant User Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long merchantUserId;
        /**
         * login Account 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String loginAccount;
        /**
         * real Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String realName;
        /**
         * mobile 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String mobile;
        /**
         * 商户管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String email;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
        /**
         * locked 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer locked;
        /**
         * last Login At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime lastLoginAt;
        /**
         * OTP 策略：OPTIONAL、REQUIRED、EXEMPT。
         */
        private String mfaPolicy;
        /**
         * OTP 状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED。
         */
        private String mfaStatus;
        /**
         * OTP 完成绑定时间。
         */
        private LocalDateTime mfaBindTime;
        /**
         * 最近一次 OTP 验证成功时间。
         */
        private LocalDateTime mfaLastVerifyTime;
        /**
         * OTP 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime mfaExemptUntil;
        /**
         * OTP 连续失败后的临时锁定截止时间。
         */
        private LocalDateTime mfaLockedUntil;
        /**
         * 是否当前登录账号，用于页面隐藏重置、豁免、停用等自我降级 OTP 操作。
         */
        private Boolean currentAccount;
        /**
         * role Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> roleIds = Collections.emptyList();
        /**
         * role Names 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<String> roleNames = Collections.emptyList();
        /**
         * dept Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> deptIds = Collections.emptyList();
        /**
         * post Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> postIds = Collections.emptyList();
        /**
         * created At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime createdAt;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaActionRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountMfaActionRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountMfaActionRequest {
        /**
         * OTP 安全操作原因，必须写明审批依据或处理背景。
         */
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must not exceed 500")
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaExemptRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountMfaExemptRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountMfaExemptRequest extends AccountMfaActionRequest {
        /**
         * OTP 豁免截止时间，空表示长期豁免。
         */
        private LocalDateTime exemptUntil;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AccountMfaStatusResponse
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AccountMfaStatusResponse 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AccountMfaStatusResponse {
        /**
         * 登录账号ID。
         */
        private Long accountId;
        /**
         * 商户员工登录账号。
         */
        private String loginAccount;
        /**
         * OTP 策略。
         */
        private String mfaPolicy;
        /**
         * OTP 状态。
         */
        private String mfaStatus;
        /**
         * 完成绑定时间。
         */
        private LocalDateTime bindTime;
        /**
         * 最近验证成功时间。
         */
        private LocalDateTime lastVerifyTime;
        /**
         * 临时锁定截止时间。
         */
        private LocalDateTime lockedUntil;
        /**
         * 豁免截止时间。
         */
        private LocalDateTime exemptUntil;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleQueryRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RoleQueryRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RoleQueryRequest extends PageRequest {
        /**
         * role Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleName;
        /**
         * role Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleCode;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
        /**
         * created Start Time 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String createdStartTime;
        /**
         * created End Time 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String createdEndTime;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RoleSaveRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RoleSaveRequest {
        /**
         * role Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleCode;
        /**
         * role Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleName;
        /**
         * data Scope 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String dataScope;
        /**
         * description 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String description;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status = 1;
        /**
         * sort No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer sortNo = 100;
        /**
         * menu Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> menuIds;
        /**
         * permission Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> permissionIds;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RoleDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RoleDTO {
        /**
         * role Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long roleId;
        /**
         * role Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleCode;
        /**
         * role Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleName;
        /**
         * role Type 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String roleType;
        /**
         * data Scope 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String dataScope;
        /**
         * description 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String description;
        /**
         * status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer status;
        /**
         * sort No 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Integer sortNo;
        /**
         * created At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime createdAt;
        /**
         * updated At 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private LocalDateTime updatedAt;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleMenuAuthDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RoleMenuAuthDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RoleMenuAuthDTO {
        /**
         * role Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long roleId;
        /**
         * checked Menu Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> checkedMenuIds = Collections.emptyList();
        /**
         * menus 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<com.scott.payment.component.db.auth.dto.AuthMenuDTO> menus = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RolePermissionAuthDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RolePermissionAuthDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RolePermissionAuthDTO {
        /**
         * role Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long roleId;
        /**
         * checked Permission Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> checkedPermissionIds = Collections.emptyList();
        /**
         * permissions 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<PermissionDTO> permissions = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleGrantTreeSaveRequest
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RoleGrantTreeSaveRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RoleGrantTreeSaveRequest {
        /**
         * menu Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> menuIds = Collections.emptyList();
        /**
         * permission Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> permissionIds = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RoleGrantTreeDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : RoleGrantTreeDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class RoleGrantTreeDTO {
        /**
         * role Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long roleId;
        /**
         * role 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private RoleDTO role;
        /**
         * checked Menu Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> checkedMenuIds = Collections.emptyList();
        /**
         * checked Permission Ids 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<Long> checkedPermissionIds = Collections.emptyList();
        /**
         * tree 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<AuthGrantNodeDTO> tree = Collections.emptyList();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : AuthGrantNodeDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : AuthGrantNodeDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class AuthGrantNodeDTO {
        /**
         * id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String id;
        /**
         * node Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long nodeId;
        /**
         * menu Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long menuId;
        /**
         * permission Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long permissionId;
        /**
         * node Type 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nodeType;
        /**
         * name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String name;
        /**
         * code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String code;
        /**
         * children 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private List<AuthGrantNodeDTO> children = new ArrayList<>();
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PermissionDTO
     * @date : 2026-06-23 12:55
     * @email : scott_x@163.com
     * @description : PermissionDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class PermissionDTO {
        /**
         * permission Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long permissionId;
        /**
         * menu Id 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private Long menuId;
        /**
         * permission Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String permissionCode;
        /**
         * permission Name 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String permissionName;
        /**
         * permission Type 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String permissionType;
        /**
         * resource Method 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String resourceMethod;
        /**
         * resource Path 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String resourcePath;
    }
}
