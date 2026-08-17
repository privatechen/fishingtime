package com.fishingtime.game.service;

import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.config.DetailProperties;
import com.fishingtime.game.domain.DetailQuestion;
import com.fishingtime.game.dto.DetailAdminImageVO;
import com.fishingtime.game.dto.DetailAdminUploadResult;
import com.fishingtime.game.mapper.DetailQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 《细节》管理后台服务
 *
 * - 鉴权：管理身份 = 当前登录用户用户名等于配置的 admin-user（默认 admin），
 *   不再用独立 admin token；管理操作走普通登录会话
 * - 上传：图片落 {image-dir}/{image_key}.jpg + 解析 30 行标准文本 → upsert detail_question
 * - image_key 由上传文件名推导：PicA.jpg → pic_a（兼容现有命名）；其他 → 小写文件名
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetailAdminService {

    private static final Pattern OPTION_PATTERN = Pattern.compile("^([ABCD])\\.\\s*(.*)$");
    private static final Pattern ANSWER_PATTERN = Pattern.compile("^答案[:：]\\s*([ABCD])");
    /** 一图至少多少道可抽题（游戏每轮从 6 题里抽 1 题） */
    private static final int MIN_QUESTIONS = 6;

    private final DetailProperties detailProperties;
    private final DetailQuestionMapper questionMapper;

    // ────────────── 鉴权 ──────────────

    /** 当前用户是否是管理员（用户名 == 配置的 admin-user） */
    public boolean isAdmin(CurrentUserInfo user) {
        return user != null && detailProperties.getAdminUser().equals(user.getUsername());
    }

    /** 校验当前用户是管理员，否则 401 */
    public void requireAdmin(CurrentUserInfo user) {
        if (!isAdmin(user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无管理员权限");
        }
    }

    // ────────────── 上传 ──────────────

    /**
     * 上传/更新：file 可选（编辑时只改题可不传，保留原图）。
     * imageKey 优先用调用方显式指定；未指定时从文件名推导（新增场景）。
     */
    public DetailAdminUploadResult upload(MultipartFile file, String text, String explicitImageKey) {
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasFile && (explicitImageKey == null || explicitImageKey.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请上传图片或指定图片标识");
        }
        String imageKey = (explicitImageKey != null && !explicitImageKey.isBlank())
                ? explicitImageKey.trim()
                : deriveImageKey(file.getOriginalFilename());
        if (imageKey.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "图片标识不能为空");
        }

        long existing = questionMapper.countByImageKey(imageKey);
        // 先解析文本（失败则整体报错、不落盘不落库，避免留下孤儿图片/半套题）
        List<DetailQuestion> questions = parseText(text, imageKey);
        if (hasFile) {
            saveImage(file, imageKey);
        } else if (existing == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "图片「" + imageKey + "」不存在，新增必须上传图片文件");
        }

        // 整图替换：无论新增还是更新，都删旧插新，保证题库与文本一致
        questionMapper.deleteByImageKey(imageKey);
        questionMapper.insertBatch(questions);

        String action = existing > 0 ? "updated" : "created";
        log.info("[细节] 管理后台 {} 图片 {}，{} 道题", existing > 0 ? "更新" : "新增", imageKey, questions.size());
        return new DetailAdminUploadResult(imageKey, action, questions.size());
    }

    /** 全部图片 + 各自题目（管理后台列表/编辑用） */
    public List<DetailAdminImageVO> listImages() {
        List<String> keys = questionMapper.selectEnabledImageKeys();
        List<DetailAdminImageVO> list = new ArrayList<>(keys.size());
        for (String key : keys) {
            List<DetailQuestion> qs = questionMapper.selectByImageKey(key);
            list.add(new DetailAdminImageVO(key, qs.size(), qs));
        }
        return list;
    }

    /** 删除一张图 + 题目 + 图片文件（文件不存在则容忍） */
    public void deleteImage(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "图片标识不能为空");
        }
        questionMapper.deleteByImageKey(imageKey);
        File f = new File(detailProperties.getImageDir(), imageKey + ".jpg");
        if (f.exists() && !f.delete()) {
            log.warn("[细节] 管理后台删除图片文件失败: {}", f);
        }
        log.info("[细节] 管理后台删除图片 {}", imageKey);
    }

    // ────────────── 内部 ──────────────

    /** image_key 推导：PicA.jpg → pic_a；其他 → 去扩展名转小写 */
    private String deriveImageKey(String filename) {
        String base = filename == null ? "" : filename.trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        if (base.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件名不能为空");
        }
        if (base.matches("(?i)Pic[A-Za-z]")) {
            return "pic_" + base.substring(3).toLowerCase();
        }
        return base.toLowerCase();
    }

    /** 图片转存为 {image-dir}/{key}.jpg（ImageIO 重编码，统一 jpg 格式） */
    private void saveImage(MultipartFile file, String imageKey) {
        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无法识别为图片（请上传 JPG/PNG）");
            }
            File dir = new File(detailProperties.getImageDir());
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片目录创建失败: " + dir);
            }
            File out = new File(dir, imageKey + ".jpg");
            if (!ImageIO.write(img, "jpg", out)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片保存失败");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片读取失败: " + e.getMessage());
        }
    }

    /**
     * 解析标准文本：每题 3 行（问题 / 选项 / 答案），忽略空行。
     * 选项行支持 A. x　B. y　C. z　D. w（按 "X." 边界切分，兼容缺空格）。
     * 任意一题解析失败则整体报错，不落库。
     */
    public List<DetailQuestion> parseText(String text, String imageKey) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        if (lines.size() % 3 != 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "文本行数不是 3 的倍数（每题 3 行 × 题数），当前 " + lines.size() + " 行");
        }
        int count = lines.size() / 3;
        if (count < MIN_QUESTIONS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "至少需要 " + MIN_QUESTIONS + " 道题（" + (MIN_QUESTIONS * 3) + " 行），当前 " + count + " 道");
        }

        List<DetailQuestion> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String qText = lines.get(i * 3);
            String optsLine = lines.get(i * 3 + 1);
            String ansLine = lines.get(i * 3 + 2);

            Map<String, String> opts = new LinkedHashMap<>();
            for (String part : optsLine.split("(?=[ABCD]\\.)")) {
                Matcher m = OPTION_PATTERN.matcher(part.trim());
                if (m.matches()) {
                    opts.put(m.group(1), m.group(2).trim());
                }
            }
            if (opts.size() != 4) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 题选项解析失败，需要 A/B/C/D 四个选项");
            }
            Matcher am = ANSWER_PATTERN.matcher(ansLine.trim());
            if (!am.matches()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 题答案解析失败（应为「答案：X」）");
            }
            String correct = am.group(1).toUpperCase();
            if (!opts.containsKey(correct)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "第 " + (i + 1) + " 题答案键 " + correct + " 不在选项中");
            }

            DetailQuestion dq = new DetailQuestion();
            dq.setImageKey(imageKey);
            dq.setQuestionText(qText);
            dq.setOptionA(opts.get("A"));
            dq.setOptionB(opts.get("B"));
            dq.setOptionC(opts.get("C"));
            dq.setOptionD(opts.get("D"));
            dq.setCorrectOption(correct);
            dq.setDifficulty("medium");
            dq.setStatus(1);
            list.add(dq);
        }
        return list;
    }
}
