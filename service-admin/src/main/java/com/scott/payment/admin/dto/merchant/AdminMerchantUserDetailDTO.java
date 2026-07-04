package com.scott.payment.admin.dto.merchant;

import com.scott.payment.admin.dto.SysMenuDTO;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserDetailDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant User Detail 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AdminMerchantUserDetailDTO {

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private AdminMerchantUserListDTO account;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private MerchantSummary merchant;
    private List<DeptSummary> depts = Collections.emptyList();
    private List<PostSummary> posts = Collections.emptyList();
    private List<RoleSummary> roles = Collections.emptyList();
    private List<SysMenuDTO> menus = Collections.emptyList();
    private List<String> permissions = Collections.emptyList();

    @Data
    public static class MerchantSummary {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantShortName;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer merchantStatus;
    }

    @Data
    public static class DeptSummary {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long deptId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String deptName;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String deptCode;
    }

    @Data
    public static class PostSummary {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long postId;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String postName;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String postCode;
    }

    @Data
    public static class RoleSummary {
        /**
         * 商户管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long roleId;
        /**
         * 商户管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String roleCode;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roleName;
        /**
         * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roleType;
        /**
         * 商户管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
    }
}
