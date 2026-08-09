package com.fishingtime.hot.util;

/**
 * 跨平台共同热点聚类的决策阈值常量。
 *
 * 收敛 HotSimilarityService / CommonHotRefiner 中散落的魔法数字，默认值与重构前完全一致。
 * 打分微权重（如关键词打分 2.0/0.5/1.5/0.15、标题 score 的 docs×idf×(1+0.35p)×len 等）留在各服务内，
 * 此处只收敛"决策阈值 / 展示结构参数"。
 */
public final class HotClusterConstants {

    private HotClusterConstants() {}

    // ── 聚类匹配阈值（HotSimilarityService）──
    /** 并入新簇的最低相似度 */
    public static final double NEW_CLUSTER_MATCH_THRESHOLD = 0.50D;
    /** 并入已有簇的最低相似度（簇内信息更充分，更宽松） */
    public static final double EXISTING_CLUSTER_MATCH_THRESHOLD = 0.40D;
    /** 孤立标题弱附加的最低相似度（第二遍） */
    public static final double WEAK_ATTACH_THRESHOLD = 0.32D;
    /** 综合相似度中"中心相似度"的权重 */
    public static final double CENTER_WEIGHT = 0.55D;
    /** 综合相似度中"两两相似度"的权重 */
    public static final double PAIR_WEIGHT = 0.45D;
    /** 任一满足即强制并入：中心相似度下限 */
    public static final double CENTER_ACCEPT_THRESHOLD = 0.62D;
    /** 任一满足即强制并入：两两相似度下限 */
    public static final double PAIR_ACCEPT_THRESHOLD = 0.72D;
    /** 判定"有用的核心词重叠"的 IDF 下限 */
    public static final double CORE_OVERLAP_MIN_IDF = 1.35D;

    // ── token 相似度（tokenSimilarity）──
    public static final double TOKEN_WEIGHTED_JACCARD_WEIGHT = 0.68D;
    public static final double TOKEN_CONTAINMENT_WEIGHT = 0.32D;

    // ── 稀有词桥接（rareSharedTokenBridge / rareCoreBridge）──
    /** 只有 IDF 足够高的共享词才可能桥接 */
    public static final double RARE_BRIDGE_MIN_IDF = 2.0D;
    public static final double RARE_BRIDGE_BASE = 0.22D;
    public static final double RARE_BRIDGE_IDF_FACTOR = 0.07D;
    public static final double RARE_BRIDGE_LEN_FACTOR = 0.015D;
    public static final double RARE_BRIDGE_CAP = 0.46D;

    // ── 簇中心词支持度（EventCluster.centerTokens）──
    /** 成员 > 2 时中心词需达到的支持比例 */
    public static final double CENTER_SUPPORT_RATIO = 0.40D;
    /** 成员数 ≤ 此值视为小簇（中心词只需少量支持） */
    public static final int CENTER_SUPPORT_SMALL_CLUSTER_MEMBERS = 2;
    /** 小簇中心词最少支持数 */
    public static final int CENTER_SUPPORT_SINGLE_MEMBER = 1;
    /** 大簇中心词最少支持数下限 */
    public static final int CENTER_SUPPORT_MULTI_MIN = 2;

    // ── 标题生成（buildClusterTitle / chooseAnchor）──
    /** 选词 IDF 下限 */
    public static final double TITLE_TOKEN_MIN_IDF = 1.20D;
    /** 标题 score 的平台数加成系数 */
    public static final double TITLE_TOKEN_PLATFORM_FACTOR = 0.35D;
    /** 标题最多包含的关键词数 */
    public static final int TITLE_MAX_TOKENS = 4;
    /** 标题候选词池大小 */
    public static final int TITLE_POOL_SIZE = 5;
    /** 标题最大字符数 */
    public static final int TITLE_MAX_CHARS = 12;
    /** 单独一个 3+ 字实体词可独立成标题（如"白海豚"） */
    public static final int TITLE_SINGLE_WORD_MIN_LEN = 3;
    /** anchor 选择的排名加成权重 */
    public static final double TITLE_ANCHOR_RANK_BONUS = 0.15D;

    // ── 精炼层（CommonHotRefiner）──
    /** 关键词 IDF 下限 */
    public static final double MIN_KEYWORD_IDF = 1.25D;
    /** 一个共同热点至少需要的跨平台共同关键词数 */
    public static final int MIN_CLUSTER_KEYWORDS = 2;
    /** 展示标题最多关键词数 */
    public static final int MAX_DISPLAY_KEYWORDS = 4;

    // ── 簇间合并（优化③，HotSimilarityService.mergeSimilarClusters）──
    /** 合并开关，置 false 一键回退到重构前行为 */
    public static final boolean CLUSTER_MERGE_ENABLED = true;
    /** 两簇"核心词相似度"达到此值即合并 */
    public static final double CLUSTER_MERGE_CORE_THRESHOLD = 0.45D;
    /** 两簇"稀有核心词桥接"达到此值即合并 */
    public static final double CLUSTER_MERGE_BRIDGE_THRESHOLD = 0.40D;
    /** 合并轮次上限（防链式合并失控） */
    public static final int CLUSTER_MERGE_MAX_ITERATIONS = 5;
}
