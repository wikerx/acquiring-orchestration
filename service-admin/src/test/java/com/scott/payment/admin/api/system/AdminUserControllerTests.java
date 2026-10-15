package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminUserApplicationService;
import com.scott.payment.admin.dto.AdminUserProfileDTO;
import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserControllerTests
 * @date : 2026-08-10 19:38
 * @email : scott_x@163.com
 * @description : 后台用户详情接口契约测试，验证账号主键经标准分层进入缓存资料查询用例
 * @status : create
 */
@Slf4j
class AdminUserControllerTests {

    /**
     * 用户详情接口必须接收 JSON 账号主键并调用应用服务返回资料。
     */
    @Test
    void shouldQueryUserProfileByAccountId() throws Exception {
        log.info("测试后台用户详情接口，关键输入: POST accountId=10001");
        AdminUserApplicationService applicationService = mock(AdminUserApplicationService.class);
        AdminUserProfileDTO profile = new AdminUserProfileDTO();
        profile.setAccountId(10001L);
        when(applicationService.getUserProfile(10001L)).thenReturn(profile);
        MockMvc mockMvc = standaloneSetup(new AdminUserController(applicationService)).build();

        mockMvc.perform(post("/admin/system/users/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":10001}"))
                .andExpect(status().isOk());

        verify(applicationService).getUserProfile(10001L);
        log.info("后台用户详情接口验证完成，结果: 请求已转发至应用服务");
    }

    /** 新增用户时角色主键必须随请求进入应用服务，以便在同一事务内完成授权。 */
    @Test
    void shouldCreateUserWithRoleIds() throws Exception {
        AdminUserApplicationService applicationService = mock(AdminUserApplicationService.class);
        doReturn(new SysUserAccountDTO()).when(applicationService).createUser(any(SysUserAccountCreateRequest.class));
        MockMvc mockMvc = standaloneSetup(new AdminUserController(applicationService)).build();

        mockMvc.perform(post("/admin/system/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginAccount":"new-user","realName":"New User","email":"new@example.com","roleIds":[11,12]}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<SysUserAccountCreateRequest> captor =
                ArgumentCaptor.forClass(SysUserAccountCreateRequest.class);
        verify(applicationService).createUser(captor.capture());
        assertEquals(List.of(11L, 12L), captor.getValue().getRoleIds());
    }

    /** 编辑用户时显式空角色集合必须保留，空集合表示清空而不是忽略角色变更。 */
    @Test
    void shouldUpdateUserWithExplicitEmptyRoleIds() throws Exception {
        AdminUserApplicationService applicationService = mock(AdminUserApplicationService.class);
        doReturn(new SysUserAccountDTO()).when(applicationService).updateUser(any(SysUserAccountUpdateRequest.class));
        MockMvc mockMvc = standaloneSetup(new AdminUserController(applicationService)).build();

        mockMvc.perform(post("/admin/system/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":10001,"realName":"Updated User","email":"updated@example.com","roleIds":[]}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<SysUserAccountUpdateRequest> captor =
                ArgumentCaptor.forClass(SysUserAccountUpdateRequest.class);
        verify(applicationService).updateUser(captor.capture());
        assertNotNull(captor.getValue().getRoleIds());
        assertTrue(captor.getValue().getRoleIds().isEmpty());
    }

    /** 新增弹窗查询角色选项时使用独立接口，不需要伪造尚不存在的账号ID。 */
    @Test
    void shouldQueryAssignableRoleOptionsForCreateDialog() throws Exception {
        AdminUserApplicationService applicationService = mock(AdminUserApplicationService.class);
        when(applicationService.userRoleOptions()).thenReturn(new SysUserRoleAuthDTO());
        MockMvc mockMvc = standaloneSetup(new AdminUserController(applicationService)).build();

        mockMvc.perform(get("/admin/system/users/roles/options"))
                .andExpect(status().isOk());

        verify(applicationService).userRoleOptions();
    }
}
