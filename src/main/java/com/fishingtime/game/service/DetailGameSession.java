package com.fishingtime.game.service;

import com.fishingtime.game.dto.DetailFinishResponse;

import java.util.List;

/**
 * 《细节》一局游戏的服务器内存会话。
 *
 * 一局固定 5 轮；每轮懒生成 6 道候选题的随机映射（题号 1~6 与问题一一对应），
 * 用户盲选一个题号后锁定，服务端记录 revealAt 作为答题计时起点。
 * 会话 30 分钟过期；服务重启即失效（符合 PRD「中途刷新=本局中断」语义）。
 */
public class DetailGameSession {

    public static final int ROUND_COUNT = 5;
    public static final int QUESTION_POOL_SIZE = 6;

    private final String sessionId;
    private final List<String> imageKeys;
    private final long createdAt;
    private final Round[] rounds = new Round[ROUND_COUNT];
    /** 结算结果缓存（finish 幂等，重复调用返回同一结果） */
    private DetailFinishResponse result;

    public DetailGameSession(String sessionId, List<String> imageKeys) {
        this.sessionId = sessionId;
        this.imageKeys = imageKeys;
        this.createdAt = System.currentTimeMillis();
        for (int i = 0; i < ROUND_COUNT; i++) {
            rounds[i] = new Round(i + 1, imageKeys.get(i));
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean expired(long ttlMs) {
        return System.currentTimeMillis() - createdAt > ttlMs;
    }

    public boolean isFinished() {
        return result != null;
    }

    public Round round(int round) {
        if (round < 1 || round > ROUND_COUNT) {
            return null;
        }
        return rounds[round - 1];
    }

    public DetailFinishResponse getResult() {
        return result;
    }

    public void setResult(DetailFinishResponse result) {
        this.result = result;
    }

    /**
     * 单轮状态。pool 在首次抽题时生成并缓存，保证重复 draw 幂等返回同一映射。
     */
    public static class Round {

        private final int round;
        private final String imageKey;
        private List<DrawnQuestion> pool;
        private int selectedNumber;
        /** 题目展示（抽题完成）时间戳，答题计时起点 */
        private long revealAt;
        private boolean answered;
        private boolean correct;
        /** 是否超时（仅作答时判定；结算时对已抽题未作答的轮次置 true） */
        private boolean timeout;
        /** 本题答题用时（毫秒，clamp [0,8000]） */
        private long elapsedMs;

        Round(int round, String imageKey) {
            this.round = round;
            this.imageKey = imageKey;
        }

        public int getRound() {
            return round;
        }

        public String getImageKey() {
            return imageKey;
        }

        public List<DrawnQuestion> getPool() {
            return pool;
        }

        public void setPool(List<DrawnQuestion> pool) {
            this.pool = pool;
        }

        public int getSelectedNumber() {
            return selectedNumber;
        }

        public void setSelectedNumber(int selectedNumber) {
            this.selectedNumber = selectedNumber;
        }

        public long getRevealAt() {
            return revealAt;
        }

        public void setRevealAt(long revealAt) {
            this.revealAt = revealAt;
        }

        public boolean isAnswered() {
            return answered;
        }

        public void setAnswered(boolean answered) {
            this.answered = answered;
        }

        public boolean isCorrect() {
            return correct;
        }

        public void setCorrect(boolean correct) {
            this.correct = correct;
        }

        public boolean isTimeout() {
            return timeout;
        }

        public void setTimeout(boolean timeout) {
            this.timeout = timeout;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }

        public void setElapsedMs(long elapsedMs) {
            this.elapsedMs = elapsedMs;
        }
    }

    /**
     * 一道已乱序展示的候选题（含正确答案键，供服务端判题）。
     */
    public static class DrawnQuestion {

        private final Long questionId;
        private final String questionText;
        /** 乱序后的 4 个选项文本 */
        private final String[] options;
        /** 与 options 对应的选项键 A/B/C/D */
        private final String[] optionKeys;
        /** 正确答案所在键（乱序后） */
        private final String correctKey;
        /** 正确答案文本 */
        private final String correctText;

        public DrawnQuestion(Long questionId, String questionText, String[] options,
                             String[] optionKeys, String correctKey, String correctText) {
            this.questionId = questionId;
            this.questionText = questionText;
            this.options = options;
            this.optionKeys = optionKeys;
            this.correctKey = correctKey;
            this.correctText = correctText;
        }

        public Long getQuestionId() {
            return questionId;
        }

        public String getQuestionText() {
            return questionText;
        }

        public String[] getOptions() {
            return options;
        }

        public String[] getOptionKeys() {
            return optionKeys;
        }

        public String getCorrectKey() {
            return correctKey;
        }

        public String getCorrectText() {
            return correctText;
        }
    }
}
