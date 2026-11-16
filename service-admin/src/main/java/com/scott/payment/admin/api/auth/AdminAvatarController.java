package com.scott.payment.admin.api.auth;

import com.scott.payment.admin.application.auth.AdminAvatarApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.upload.ImageResponses;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 复用管理类会话鉴权与 CSRF 校验，上传和删除只操作本人。 */
@RestController
@RequestMapping("/admin/auth/avatar")
public class AdminAvatarController {
    private final AdminAvatarApplicationService avatars;
    public AdminAvatarController(AdminAvatarApplicationService avatars) { this.avatars = avatars; }
    @PostMapping(consumes = "multipart/form-data")
    @OperationLog(moduleName = "个人中心", businessType = OperationTypeConstants.UPDATE, operation = "更换个人头像")
    public CommonResult<String> upload(@RequestParam("file") MultipartFile file) { return CommonResult.success(avatars.replace(file)); }
    @DeleteMapping
    @OperationLog(moduleName = "个人中心", businessType = OperationTypeConstants.UPDATE, operation = "移除个人头像")
    public CommonResult<Void> remove() { avatars.remove(); return CommonResult.success(); }
    @GetMapping
    public ResponseEntity<byte[]> read() { return ImageResponses.png(avatars.read()); }
}
