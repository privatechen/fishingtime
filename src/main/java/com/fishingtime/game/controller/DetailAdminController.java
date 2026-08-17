package com.fishingtime.game.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.game.dto.DetailAdminImageVO;
import com.fishingtime.game.dto.DetailAdminUploadResult;
import com.fishingtime.game.service.DetailAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 《细节》管理后台 API
 *
 * 管理身份 = 当前登录用户用户名 == 配置 app.detail.admin-user（默认 admin）。
 * 所有管理操作走普通登录会话（@CurrentUser），不再有独立 admin 登录。
 *
 * GET    /api/games/detail/admin/status          — 当前用户是否管理员（公开）
 * GET    /api/games/detail/admin/images          — 全部图片 + 题目（需管理员）
 * POST   /api/games/detail/admin/upload          — 上传图片+文本 或 只改题（需管理员）
 * DELETE /api/games/detail/admin/images/{key}    — 删除图片+题目（需管理员）
 */
@Slf4j
@RestController
@RequestMapping("/api/games/detail/admin")
@RequiredArgsConstructor
public class DetailAdminController {

    private final DetailAdminService adminService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> status(@CurrentUser CurrentUserInfo currentUser) {
        return ApiResponse.success(Map.of("isAdmin", adminService.isAdmin(currentUser)));
    }

    @GetMapping("/images")
    public ApiResponse<List<DetailAdminImageVO>> listImages(@CurrentUser CurrentUserInfo currentUser) {
        adminService.requireAdmin(currentUser);
        return ApiResponse.success(adminService.listImages());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DetailAdminUploadResult> upload(@CurrentUser CurrentUserInfo currentUser,
                                                       @RequestParam(value = "file", required = false) MultipartFile file,
                                                       @RequestParam("text") String text,
                                                       @RequestParam(value = "imageKey", required = false) String imageKey) {
        adminService.requireAdmin(currentUser);
        return ApiResponse.success(adminService.upload(file, text, imageKey));
    }

    @DeleteMapping("/images/{imageKey}")
    public ApiResponse<Void> deleteImage(@CurrentUser CurrentUserInfo currentUser,
                                         @PathVariable String imageKey) {
        adminService.requireAdmin(currentUser);
        adminService.deleteImage(imageKey);
        return ApiResponse.success();
    }
}
