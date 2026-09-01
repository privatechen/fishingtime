package com.fishingtime.hot.util;

/**
 * 跨平台共同热点聚类的决策阈值常量。
 */
public final class HotClusterConstants {

    private HotClusterConstants() {}

    public static final double NEW_CLUSTER_MATCH_THRESHOLD = 0.50D;
    public static final double EXISTING_CLUSTER_MATCH_THRESHOLD = 0.40D;
    public static final double WEAK_ATTACH_THRESHOLD = 0.32D;
    public static final double CENTER_WEIGHT = 0.55D;
    public static final double PAIR_WEIGHT = 0.45D;
    public static final double CENTER_ACCEPT_THRESHOLD = 0.62D;
    public static final double PAIR_ACCEPT_THRESHOLD = 0.72D;
    public static final double CORE_OVERLAP_MIN_IDF = 1.35D;

    public static final double TOKEN_WEIGHTED_JACCARD_WEIGHT = 0.68D;
    public static final double TOKEN_CONTAINMENT_WEIGHT = 0.32D;

    public static final double RARE_BRIDGE_MIN_IDF = 2.0D;
    public static final double RARE_BRIDGE_BASE = 0.22D;
    public static final double RARE_BRIDGE_IDF_FACTOR = 0.07D;
    public static final double RARE_BRIDGE_LEN_FACTOR = 0.015D;
    public static final double RARE_BRIDGE_CAP = 0.46D;

    public static final double CENTER_SUPPORT_RATIO = 0.40D;
    public static final int CENTER_SUPPORT_SMALL_CLUSTER_MEMBERS = 2;
    public static final int CENTER_SUPPORT_SINGLE_MEMBER = 1;
    public static final int CENTER_SUPPORT_MULTI_MIN = 2;

    public static final double TITLE_TOKEN_MIN_IDF = 1.20D;
    public static final double TITLE_TOKEN_PLATFORM_FACTOR = 0.35D;
    public static final int TITLE_MAX_TOKENS = 4;
    public static final int TITLE_POOL_SIZE = 5;
    public static final int TITLE_MAX_CHARS = 12;
    public static final int TITLE_SINGLE_WORD_MIN_LEN = 3;
    public static final double TITLE_ANCHOR_RANK_BONUS = 0.15D;

    public static final double MIN_KEYWORD_IDF = 1.25D;
    public static final int MIN_CLUSTER_KEYWORDS = 2;
    public static final int MAX_DISPLAY_KEYWORDS = 4;

    // V1.5：严格双关键词失败后，允许“一个强实体 + 同事件语义”兜底召回。
    public static final double STRONG_ENTITY_MIN_IDF = 2.20D;
    public static final int STRONG_ENTITY_MIN_LEN = 3;
    public static final int STRONG_ENTITY_MIN_PLATFORMS = 2;
    /** 没有事件动作同义命中时，除强实体外至少还要有 1 个有效共同词。 */
    public static final int STRONG_ENTITY_RESIDUAL_OVERLAP = 1;

    public static final boolean CLUSTER_MERGE_ENABLED = true;
    public static final double CLUSTER_MERGE_CORE_THRESHOLD = 0.45D;
    public static final double CLUSTER_MERGE_BRIDGE_THRESHOLD = 0.40D;
    public static final int CLUSTER_MERGE_MAX_ITERATIONS = 5;
}
