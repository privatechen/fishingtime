package com.fishingtime.game.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.game.domain.DetailQuestion;
import com.fishingtime.game.domain.DetailScore;
import com.fishingtime.game.dto.DetailAnswerResponse;
import com.fishingtime.game.dto.DetailDrawResponse;
import com.fishingtime.game.dto.DetailFinishResponse;
import com.fishingtime.game.dto.DetailRoundInfo;
import com.fishingtime.game.dto.DetailRoundResult;
import com.fishingtime.game.dto.DetailStartResponse;
import com.fishingtime.game.leaderboard.LeaderboardService;
import com.fishingtime.game.leaderboard.LocalRankingCache;
import com.fishingtime.game.mapper.DetailQuestionMapper;
import com.fishingtime.game.mapper.DetailScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 《细节》游戏服务 — 服务端权威判定。
 *
 * 核心职责：
 * - start：从启用题目涉及的图片池随机抽 5 张图建立一局会话
 * - draw：为当前轮懒生成 6 道候选题的随机映射，用户盲选题号后返回问题（不含答案）
 * - answer：服务端判题，答题计时 = 题目展示(revealAt) 到提交，clamp [0,8000]，超时判错
 * - finish：汇总答对数 + 累计用时，登录则落库并返回今日/总排名
 *
 * 防刷：答案与计时均服务端计算，客户端只回传选项键；重复 answer/finish 幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetailGameService {

    /** 每轮观察时间（秒）—— 固定，不参与排名 */
    public static final int OBSERVATION_MS = 10_000;
    /** 单题答题时间上限（毫秒），超时按此计入本题用时 */
    public static final int ANSWER_TIME_LIMIT_MS = 8_000;
    /** 网络/渲染延迟宽容（毫秒），超过该值判超时，防止正常作答被误判 */
    private static final int ANSWER_GRACE_MS = 1_500;
    /** 会话 TTL：30 分钟 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;
    /** 游戏标识（game_score.game_code / 排行榜注册名） */
    public static final String GAME_CODE = "detail";

    private final Map<String, DetailGameSession> sessions = new ConcurrentHashMap<>();

    private final DetailQuestionMapper questionMapper;
    private final DetailScoreMapper detailScoreMapper;
    private final GameScoreLogService gameScoreLogService;
    private final LeaderboardService leaderboardService;
    private final LocalRankingCache rankingCache;

    // ────────────── 开局 ──────────────

    public DetailStartResponse start() {
        List<String> imageKeys = questionMapper.selectEnabledImageKeys();
        if (imageKeys.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题库为空，请先录入题目");
        }

        // 一局内不重复抽图：图池 ≥5 时随机打乱取 5 张（每张恰好一次）；不足 5 张时剩余轮次允许重复兜底
        Collections.shuffle(imageKeys);
        List<String> picked = new ArrayList<>(DetailGameSession.ROUND_COUNT);
        int distinct = Math.min(imageKeys.size(), DetailGameSession.ROUND_COUNT);
        for (int i = 0; i < distinct; i++) {
            picked.add(imageKeys.get(i));
        }
        while (picked.size() < DetailGameSession.ROUND_COUNT) {
            picked.add(imageKeys.get((int) (Math.random() * imageKeys.size())));
        }
        if (distinct < DetailGameSession.ROUND_COUNT) {
            log.warn("[细节] 可玩图片仅 {} 张（需 {} 张），本局存在重复图片", distinct, DetailGameSession.ROUND_COUNT);
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        DetailGameSession session = new DetailGameSession(sessionId, picked);
        sessions.put(sessionId, session);

        List<DetailRoundInfo> rounds = new ArrayList<>(DetailGameSession.ROUND_COUNT);
        for (int i = 0; i < DetailGameSession.ROUND_COUNT; i++) {
            rounds.add(new DetailRoundInfo(i + 1, picked.get(i), imageUrl(picked.get(i))));
        }
        log.info("[细节] 开局 {} 轮图：{}", sessionId, picked);
        return new DetailStartResponse(sessionId, OBSERVATION_MS, rounds);
    }

    // ────────────── 抽题 ──────────────

    public DetailDrawResponse draw(String sessionId, int round, int number) {
        DetailGameSession session = requireSession(sessionId);
        synchronized (session) {
            if (session.isFinished()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "本局已结算");
            }
            DetailGameSession.Round r = requireRound(session, round);
            if (number < 1 || number > DetailGameSession.QUESTION_POOL_SIZE) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "题号不合法");
            }
            if (r.isAnswered()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "本回合已作答，不可再抽题");
            }
            // 首抽：懒生成 6 题映射并锁定；重复抽同一题号幂等，换题号拒绝（PRD A7 选中不能换题）
            if (r.getPool() == null) {
                r.setPool(drawPool(r.getImageKey()));
                r.setSelectedNumber(number);
                r.setRevealAt(System.currentTimeMillis());
                log.debug("[细节] 会话 {} 第{}轮 抽到题号 {}", sessionId, round, number);
            } else if (r.getSelectedNumber() != number) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "题号已锁定，不能更换");
            }
            DetailGameSession.DrawnQuestion dq = r.getPool().get(number - 1);
            return new DetailDrawResponse(dq.getQuestionId(), dq.getQuestionText(), dq.getOptions(), dq.getOptionKeys());
        }
    }

    // ────────────── 作答 ──────────────

    public DetailAnswerResponse answer(String sessionId, int round, String option) {
        DetailGameSession session = requireSession(sessionId);
        synchronized (session) {
            if (session.isFinished()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "本局已结算");
            }
            DetailGameSession.Round r = requireRound(session, round);
            if (r.getPool() == null || r.getSelectedNumber() == 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "请先抽题");
            }
            DetailGameSession.DrawnQuestion dq = r.getPool().get(r.getSelectedNumber() - 1);

            // 幂等：该轮已作答，返回首次结果
            if (r.isAnswered()) {
                return new DetailAnswerResponse(r.isCorrect(), dq.getCorrectKey(), dq.getCorrectText(), r.getElapsedMs());
            }

            long rawElapsed = System.currentTimeMillis() - r.getRevealAt();
            boolean timeout = rawElapsed > ANSWER_TIME_LIMIT_MS + ANSWER_GRACE_MS;
            long elapsed = Math.max(0, Math.min(rawElapsed, ANSWER_TIME_LIMIT_MS));

            boolean correct;
            if (timeout) {
                correct = false;
                elapsed = ANSWER_TIME_LIMIT_MS;
            } else {
                correct = option != null && option.equals(dq.getCorrectKey());
            }

            r.setAnswered(true);
            r.setCorrect(correct);
            r.setTimeout(timeout);
            r.setElapsedMs(elapsed);
            log.info("[细节] 会话 {} 第{}轮 {}，用时 {}ms", sessionId, round, correct ? "答对" : (timeout ? "超时" : "答错"), elapsed);
            return new DetailAnswerResponse(correct, dq.getCorrectKey(), dq.getCorrectText(), elapsed);
        }
    }

    // ────────────── 结算 ──────────────

    public DetailFinishResponse finish(String sessionId, Long userId) {
        DetailGameSession session = requireSession(sessionId);
        synchronized (session) {
            // 已落库的结算结果幂等返回；匿名（未保存）的结算不缓存，
            // 便于用户登录后再次 finish 重算并补存成绩
            if (session.isFinished()) {
                return session.getResult();
            }

            int correctCount = 0;
            int answeredCount = 0;
            int totalMs = 0;
            List<DetailRoundResult> roundResults = new ArrayList<>(DetailGameSession.ROUND_COUNT);
            for (int i = 1; i <= DetailGameSession.ROUND_COUNT; i++) {
                DetailGameSession.Round r = session.round(i);
                boolean played = true;
                if (!r.isAnswered()) {
                    if (r.getPool() == null) {
                        // 未抽题的轮次：直接不计入（中途结束保存时不算惩罚分）
                        played = false;
                    } else {
                        // 已抽题但未作答：按超时答错，上限计入用时
                        r.setAnswered(true);
                        r.setCorrect(false);
                        r.setTimeout(true);
                        r.setElapsedMs(ANSWER_TIME_LIMIT_MS);
                    }
                }
                if (played) {
                    answeredCount++;
                    if (r.isCorrect()) {
                        correctCount++;
                    }
                    totalMs += r.getElapsedMs();
                }
                roundResults.add(new DetailRoundResult(i, played, r.isCorrect(), r.isTimeout(), r.getElapsedMs()));
            }

            boolean saved = false;
            Integer bestCorrect = null;
            Integer bestMs = null;
            Integer todayRank = null;
            Integer allRank = null;
            if (userId != null) {
                saveResult(userId, correctCount, totalMs);
                DetailScore after = detailScoreMapper.selectByUserId(userId);
                bestCorrect = after.getBestCorrectCount();
                bestMs = after.getBestAnswerTimeMs();
                // 今日/总榜缓存失效后重建，返回本局后的真实名次
                rankingCache.invalidate(GAME_CODE, "TODAY");
                rankingCache.invalidate(GAME_CODE, "ALL");
                todayRank = rankOf(userId, "TODAY");
                allRank = rankOf(userId, "ALL");
                saved = true;
                log.info("[细节] 会话 {} 结算：答对 {}/{} 用时 {}ms，今日第{}名 总榜第{}名",
                        sessionId, correctCount, answeredCount, totalMs, todayRank, allRank);
            }

            DetailFinishResponse result = new DetailFinishResponse(correctCount, answeredCount, totalMs, saved, bestCorrect, bestMs, todayRank, allRank, roundResults);
            // 仅已落库的结算才缓存（幂等）；匿名未保存的不缓存，登录后补存走重算
            if (userId != null) {
                session.setResult(result);
            }
            return result;
        }
    }

    // ────────────── 内部 ──────────────

    private List<DetailGameSession.DrawnQuestion> drawPool(String imageKey) {
        List<DetailQuestion> enabled = questionMapper.selectEnabledByImageKey(imageKey);
        if (enabled.size() < DetailGameSession.QUESTION_POOL_SIZE) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片 " + imageKey + " 启用题目不足 " + DetailGameSession.QUESTION_POOL_SIZE + " 道");
        }
        Collections.shuffle(enabled);
        List<DetailGameSession.DrawnQuestion> pool = new ArrayList<>(DetailGameSession.QUESTION_POOL_SIZE);
        for (DetailQuestion q : enabled.subList(0, DetailGameSession.QUESTION_POOL_SIZE)) {
            pool.add(shuffleOptions(q));
        }
        return pool;
    }

    /** 4 个选项乱序展示，正确答案键随之变化（防「固定位置答案」记忆） */
    private DetailGameSession.DrawnQuestion shuffleOptions(DetailQuestion q) {
        String[] keys = {"A", "B", "C", "D"};
        String[] texts = {q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()};

        List<Integer> order = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            order.add(i);
        }
        Collections.shuffle(order);

        int correctIdx = "ABCD".indexOf(q.getCorrectOption());
        String[] options = new String[4];
        String[] optionKeys = new String[4];
        String correctKey = null;
        String correctText = null;
        for (int i = 0; i < 4; i++) {
            int src = order.get(i);
            optionKeys[i] = keys[src];
            options[i] = texts[src];
            if (src == correctIdx) {
                correctKey = keys[src];
                correctText = texts[src];
            }
        }
        return new DetailGameSession.DrawnQuestion(q.getId(), q.getQuestionText(), options, optionKeys, correctKey, correctText);
    }

    /** 落库：detail_score（总榜）+ game_score（今日榜日志） */
    private void saveResult(Long userId, int correctCount, int totalMs) {
        DetailScore existing = detailScoreMapper.selectByUserId(userId);
        if (existing == null) {
            DetailScore ds = new DetailScore();
            ds.setUserId(userId);
            ds.setBestCorrectCount(correctCount);
            ds.setBestAnswerTimeMs(totalMs);
            detailScoreMapper.insert(ds);
        } else {
            detailScoreMapper.updateBest(userId, correctCount, totalMs);
        }
        gameScoreLogService.record(userId, GAME_CODE, correctCount, totalMs);
    }

    /** 取用户指定榜单名次（无记录返回 null） */
    private Integer rankOf(Long userId, String period) {
        var lb = leaderboardService.getLeaderboard(GAME_CODE, period, 1, 20, userId);
        return lb.getMyRank() != null ? lb.getMyRank().getRank() : null;
    }

    private DetailGameSession requireSession(String sessionId) {
        DetailGameSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或已过期");
        }
        return session;
    }

    private DetailGameSession.Round requireRound(DetailGameSession session, int round) {
        DetailGameSession.Round r = session.round(round);
        if (r == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "轮次不合法");
        }
        return r;
    }

    /** 图片访问地址：由 image_key 推导静态资源路径（本地素材为 ~20KB 的 jpg，减小传输体积） */
    private String imageUrl(String imageKey) {
        return "/games/detail/" + imageKey + ".jpg";
    }

    /** 过期会话清理：30 分钟未操作即销毁 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().expired(SESSION_TTL_MS));
        if (sessions.size() != before) {
            log.info("[细节] 清理过期会话 {} → {}", before, sessions.size());
        }
    }
}
