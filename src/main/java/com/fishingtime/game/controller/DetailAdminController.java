package com.fishingtime.game.controller;

import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.game.dto.DetailAdminLoginRequest;
import com.fishingtime.game.dto.DetailAdminUploadResult;
import com.fishingtime.game.service.DetailAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 《细节》管理后台 API（独立于用户登录，凭据来自配置 app.detail.admin-*）
 *
 * POST /api/games/detail/admin/login   — admin/admin 登录，返回内存 token
 * POST /api/games/detail/admin/upload  — 上传图片+标准文本，需带 X-Admin-Token
 *    文件：file（图片）+ text（30 行标准文本）→ 保存图片 + upsert detail_question
 */
@Slf4j
@RestController
@RequestMapping("/api/games/detail/admin")
@RequiredArgsConstructor
public class DetailAdminController {

    private final DetailAdminService adminService;

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody DetailAdminLoginRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请输入账号和密码");
        }
        String token = adminService.login(request.getUsername(), request.getPassword());
        return ApiResponse.success(Map.of("token", token));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DetailAdminUploadResult> upload(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("text") String text) {
        adminService.requireToken(token);
        return ApiResponse.success(adminService.upload(file, text));
    }
}
