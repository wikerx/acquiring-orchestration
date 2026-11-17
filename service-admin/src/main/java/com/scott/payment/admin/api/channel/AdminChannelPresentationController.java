package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelPresentationApplicationService;
import com.scott.payment.admin.dto.channel.ChannelPresentationDTOs.*;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.upload.ImageResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 应用服务按 scope 检查对应页面的 list/detail 权限，不复用配置编辑权限。 */
@RestController
@RequestMapping("/admin/channel/presentation")
public class AdminChannelPresentationController {
    private final AdminChannelPresentationApplicationService presentation;
    public AdminChannelPresentationController(AdminChannelPresentationApplicationService presentation) {
        this.presentation = presentation;
    }

    @PostMapping("/search")
    public CommonResult<List<Presentation>> lookup(@RequestParam("scope") Scope scope,
                                                   @RequestBody LookupRequest request) {
        return CommonResult.success(presentation.lookup(scope, request));
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@RequestParam("scope") Scope scope, @PathVariable("id") Long id,
                                       @RequestParam("version") String version) {
        return ImageResponses.png(presentation.logo(scope, id, version));
    }
}
