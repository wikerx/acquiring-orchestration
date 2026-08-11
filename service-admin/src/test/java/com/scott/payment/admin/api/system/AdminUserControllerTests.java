package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminUserApplicationService;
import com.scott.payment.admin.dto.AdminUserProfileDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

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
}
