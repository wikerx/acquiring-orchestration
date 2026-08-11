package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserProfileDTO
 * @date : 2026-08-10 18:02
 * @email : scott_x@163.com
 * @description : 运营后台用户维护资料读模型，作为永久 Redis 缓存 Value 和用户详情响应
 * @status : create
 *
 * <p>该模型只保存用户维护页面需要的资料和关联主键。禁止加入密码哈希、Salt、TOTP Secret、
 * 登录失败次数、锁定状态、登录 IP、Token 或会话等鉴权事实。</p>
 */
@Data
public class AdminUserProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 后台账号主键，不允许为空。 */
    private Long accountId;

    /** 用户主体主键，不允许为空。 */
    private Long userId;

    /** 所属部门主键，未分配部门时允许为空。 */
    private Long deptId;

    /** 已分配岗位主键集合，不包含岗位名称等可变元数据。 */
    private List<Long> postIds = new ArrayList<>();

    /** 已分配角色主键集合，不包含权限和当前操作人可授权范围。 */
    private List<Long> roleIds = new ArrayList<>();

    /** 后台登录账号，不允许为空。 */
    private String loginAccount;

    /** 用户真实姓名，允许为空。 */
    private String realName;

    /** 联系手机号，属于受保护资料，禁止写入业务日志。 */
    private String mobile;

    /** 联系邮箱，属于受保护资料，禁止写入业务日志。 */
    private String email;

    /** 用户类型，例如 PLATFORM。 */
    private String userType;

    /** 账号状态：1 启用，0 停用。 */
    private Integer status;

    /** 管理端维护备注，允许为空且禁止无条件写入日志。 */
    private String remark;

    /** 账号创建时间，精确到数据库保存的毫秒。 */
    private LocalDateTime createdAt;
}
