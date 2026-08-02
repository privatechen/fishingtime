package com.fishingtime.banner.service.impl;

import com.fishingtime.banner.dto.DailySentenceDTO;
import com.fishingtime.banner.mapper.DailySentenceMapper;
import com.fishingtime.banner.service.DailySentenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日一句服务实现
 *
 * 优化点：
 * - 启动时一次性把句子加载到内存，接口从内存读取，无 DB 延迟
 * - 每天展示固定一条（用日期决定索引），跨天自动切换
 */
@Slf4j
@Service
public class DailySentenceServiceImpl implements DailySentenceService {

    /** 数据库为空时的默认文案 */
    private static final String DEFAULT_SENTENCE = "今天也要开心一点～";

    private final DailySentenceMapper dailySentenceMapper;

    /** 内存缓存：所有启用的句子 */
    private List<String> cache = new ArrayList<>();

    /** 当天句子 */
    private String todaySentence;

    /** 当前缓存的日期 key */
    private String todayKey;

    public DailySentenceServiceImpl(DailySentenceMapper dailySentenceMapper) {
        this.dailySentenceMapper = dailySentenceMapper;
    }

    /** 启动时加载所有启用的句子到内存 */
    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public DailySentenceDTO getRandom() {
        return buildDTO(getTodaySentence(), null);
    }

    /**
     * 获取当天句子
     * 同一自然日内固定返回同一条，跨天自动切换
     */
    private String getTodaySentence() {
        String key = LocalDate.now().toString();
        if (!key.equals(todayKey)) {
            todaySentence = selectForDate(key);
            todayKey = key;
        }
        return todaySentence;
    }

    /** 根据日期选一条句子 */
    private String selectForDate(String dateKey) {
        if (cache.isEmpty()) {
            log.debug("[今日一句] 数据库为空，返回默认文案");
            return DEFAULT_SENTENCE;
        }
        long day = LocalDate.parse(dateKey).toEpochDay();
        return cache.get((int) (Math.floorMod(day, cache.size())));
    }

    /** 重新加载缓存 */
    private void reload() {
        try {
            List<String> loaded = dailySentenceMapper.selectAllEnabledContents();
            cache = (loaded != null) ? loaded : new ArrayList<>();
            todaySentence = null;
            log.info("[今日一句] 加载 {} 条句子到内存", cache.size());
        } catch (Exception e) {
            log.error("[今日一句] 加载缓存失败: {}", e.getMessage());
            cache = new ArrayList<>();
        }
    }

    private DailySentenceDTO buildDTO(String content, String category) {
        DailySentenceDTO dto = new DailySentenceDTO();
        dto.setContent(content);
        dto.setCategory(category);
        return dto;
    }
}
