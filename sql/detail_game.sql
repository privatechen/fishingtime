-- =============================================================
-- 《细节》小游戏 建表 + 题库 seed（PRD《细节》V1.0）
-- 目标库：fishingtime
-- 说明：图片不入库，由 static/games/detail/{image_key}.png 托管；
--       本脚本只建题库表 + 每用户最佳成绩表，并录入 5 图 × 10 题 = 50 道候选题。
-- 排序规则：答对数 DESC → 累计答题用时 ASC → 达成时间 ASC
-- =============================================================

-- ---------------------------------------------------------
-- 细节游戏题库表（PRD §14：image_key 关联本地服务器图片文件）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `detail_question` (
    `id`              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `image_key`       VARCHAR(32)  NOT NULL             COMMENT '图片唯一标识（pic_a/pic_b/pic_c/pic_d/pic_e），对应 static/games/detail/ 下图片',
    `question_text`   VARCHAR(255) NOT NULL             COMMENT '题目文本',
    `option_a`        VARCHAR(128) NOT NULL             COMMENT '选项 A',
    `option_b`        VARCHAR(128) NOT NULL             COMMENT '选项 B',
    `option_c`        VARCHAR(128) NOT NULL             COMMENT '选项 C',
    `option_d`        VARCHAR(128) NOT NULL             COMMENT '选项 D',
    `correct_option`  CHAR(1)      NOT NULL             COMMENT '正确答案 A/B/C/D',
    `difficulty`      VARCHAR(16)  NOT NULL DEFAULT 'medium' COMMENT '简单/中等/较难（V1 随机选图暂不参与，仅存档）',
    `status`          TINYINT      NOT NULL DEFAULT 1   COMMENT '1=启用 0=停用（抽题只取启用题目）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    KEY `idx_image_status` (`image_key`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='细节游戏题库表（每图 10 道候选题）';

-- ---------------------------------------------------------
-- 细节游戏最佳成绩表（每用户一行）
-- 排行规则：best_correct_count 降序 → best_answer_time_ms 升序 → achieved_at 升序
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `detail_score` (
    `id`                   BIGINT      PRIMARY KEY AUTO_INCREMENT,
    `user_id`              BIGINT      NOT NULL             COMMENT '关联 user.id，一人一行',
    `best_correct_count`   INT         NOT NULL DEFAULT 0   COMMENT '最佳答对题数（排行第一依据，越大越好）',
    `best_answer_time_ms`  INT         NOT NULL DEFAULT 0   COMMENT '最佳成绩的累计答题用时（毫秒，越小越好，排行第二依据）',
    `achieved_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '刷新该最佳的时间',
    `created_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_best_correct_count` (`best_correct_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='细节游戏最佳成绩表（每用户一行）';

-- ---------------------------------------------------------
-- game_score.game_code 注释补充 detail（仅改注释，不影响结构）
-- ---------------------------------------------------------
ALTER TABLE `game_score`
    MODIFY COLUMN `game_code` VARCHAR(32) NOT NULL COMMENT '2048/color-focus/direction-trap/color-hunter/fish-breakout/extreme-fishing/detail';

-- ---------------------------------------------------------
-- 题库 seed：5 图 × 10 题（来源：方案/Q&A）
-- ---------------------------------------------------------
INSERT INTO `detail_question`
  (`image_key`, `question_text`, `option_a`, `option_b`, `option_c`, `option_d`, `correct_option`, `difficulty`)
VALUES
('pic_a', '骑自行车的小男孩穿的是什么颜色的上衣？', '蓝色', '橙色', '绿色', '红色', 'B', 'medium'),
('pic_a', '长椅上的老人正在做什么？', '吃东西', '看手机', '看报纸', '喝水', 'C', 'medium'),
('pic_a', '池塘里有几只白色的天鹅？', '1只', '2只', '3只', '4只', 'B', 'medium'),
('pic_a', '放风筝的小女孩穿的是什么颜色的上衣？', '粉色', '黄色', '蓝色', '绿色', 'A', 'medium'),
('pic_a', '野餐垫旁边的狗脖子上戴着什么？', '蓝色项圈', '红色围巾', '黄色铃铛', '绿色领结', 'B', 'medium'),
('pic_a', '野餐的小女孩手里拿着什么？', '香蕉', '面包', '苹果', '水杯', 'C', 'medium'),
('pic_a', '右侧蹲在地上的男孩正在做什么？', '喂鸟', '捡花', '系鞋带', '玩玩具', 'A', 'medium'),
('pic_a', '大树上的鸟窝旁边有什么动物？', '松鼠', '蓝色小鸟', '猫', '蝴蝶', 'B', 'medium'),
('pic_a', '冰淇淋车的遮阳棚是什么颜色相间的？', '蓝白', '黄白', '红白', '绿白', 'C', 'medium'),
('pic_a', '图片右下角出现了哪种动物？', '兔子', '松鼠', '小猫', '刺猬', 'B', 'medium'),
('pic_b', '天空中一共有几只海鸥？', '2只', '3只', '4只', '5只', 'B', 'medium'),
('pic_b', '海边小屋的遮阳棚是什么颜色相间的？', '红白', '黄白', '蓝白', '绿白', 'C', 'medium'),
('pic_b', '正在奔跑的小男孩穿什么颜色的上衣？', '绿色', '黄色', '蓝色', '红色', 'A', 'medium'),
('pic_b', '沙滩中央的小女孩正在做什么？', '捡贝壳', '堆沙堡', '看书', '玩球', 'B', 'medium'),
('pic_b', '躺在沙滩椅上的女士正在做什么？', '喝饮料', '看手机', '看书', '睡觉', 'C', 'medium'),
('pic_b', '女士头上戴着什么？', '蓝色棒球帽', '红色帽子', '草帽', '没有戴帽子', 'C', 'medium'),
('pic_b', '沙滩上奔跑的狗脖子上戴着什么颜色的项圈？', '蓝色', '红色', '黄色', '绿色', 'B', 'medium'),
('pic_b', '远处灯塔顶部是什么颜色？', '蓝色', '黄色', '红色', '绿色', 'C', 'medium'),
('pic_b', '图片最下方中央附近有什么动物？', '海龟', '螃蟹', '海星', '小鱼', 'B', 'medium'),
('pic_b', '右下角的小男孩正在做什么？', '堆沙堡', '玩沙滩球', '捡/摆贝壳', '挖水沟', 'C', 'medium'),
('pic_c', '男孩写字时使用的铅笔是什么颜色？', '红色', '黄色', '绿色', '蓝色', 'C', 'medium'),
('pic_c', '书桌左侧最上面一本书的封面是什么颜色？', '绿色', '蓝色', '黄色', '红色', 'A', 'medium'),
('pic_c', '书架上的恐龙在玩具熊的哪一侧？', '左侧', '右侧', '上方', '下方', 'B', 'medium'),
('pic_c', '玩具熊脖子上戴着什么颜色的蝴蝶结？', '黄色', '蓝色', '红色', '绿色', 'C', 'medium'),
('pic_c', '书包正面最大的图案是什么？', '月亮', '星星', '火箭', '足球', 'B', 'medium'),
('pic_c', '墙上的时钟外框是什么颜色？', '红色', '绿色', '黄色', '蓝色', 'D', 'medium'),
('pic_c', '床头柜的抽屉是什么颜色？', '蓝色', '白色', '黄色', '绿色', 'A', 'medium'),
('pic_c', '窗帘上的主要图案是什么？', '月亮', '云朵', '星星', '火箭', 'C', 'medium'),
('pic_c', '床头电子钟显示的时间是多少？', '20:10', '20:20', '20:30', '20:40', 'C', 'medium'),
('pic_c', '墙上的火箭图片中，火箭头部主要是什么颜色？', '红色', '黄色', '蓝色', '绿色', 'A', 'medium'),
('pic_d', '女孩的围裙是什么颜色？', '蓝色', '粉色', '绿色', '黄色', 'B', 'medium'),
('pic_d', '女孩正在切什么食物？', '黄瓜', '苹果', '番茄', '土豆', 'C', 'medium'),
('pic_d', '冰箱是什么颜色？', '白色', '黄色', '浅蓝色', '绿色', 'C', 'medium'),
('pic_d', '冰箱上蓝色磁贴是什么形状？', '爱心', '星星', '圆形', '月亮', 'B', 'medium'),
('pic_d', '猫睡在什么颜色的垫子上？', '黄色', '红色', '绿色', '蓝色', 'D', 'medium'),
('pic_d', '水槽旁边的洗手液瓶是什么颜色？', '绿色', '蓝色', '粉色', '黄色', 'A', 'medium'),
('pic_d', '墙上置物架除了植物外，还放了什么？', '一本书', '一个杯子', '一个碗', '一个水壶', 'B', 'medium'),
('pic_d', '挂在水槽下方的毛巾是什么颜色？', '黄色', '蓝色', '白色', '粉色', 'A', 'medium'),
('pic_d', '女孩头发上的发圈是什么颜色？', '蓝色', '黄色', '粉红色', '绿色', 'C', 'medium'),
('pic_d', '墙上的时钟外框是什么颜色？', '黄色', '蓝色', '红色', '绿色', 'B', 'medium'),
('pic_e', '沙发左侧的抱枕是什么颜色？', '蓝色', '黄色', '绿色', '白色', 'B', 'medium'),
('pic_e', '沙发右侧抱枕上是什么图案？', '星星', '圆点', '蓝色横条纹', '花朵', 'C', 'medium'),
('pic_e', '茶几上的杯子有什么图案？', '月亮', '星星', '小花', '爱心', 'B', 'medium'),
('pic_e', '茶几上的书是什么颜色？', '绿色', '黄色', '蓝色', '红色', 'C', 'medium'),
('pic_e', '猫睡在什么颜色的垫子上？', '蓝色', '黄色', '绿色', '白色', 'C', 'medium'),
('pic_e', '电视下面放着什么东西？', '一本书', '黑色设备', '花盆', '相框', 'B', 'medium'),
('pic_e', '墙上的装饰画画的是什么？', '一棵树', '一只猫', '一朵黄色的花', '一座房子', 'C', 'medium'),
('pic_e', '墙上时钟的外框是什么颜色？', '黄色', '蓝色', '绿色', '红色', 'B', 'medium'),
('pic_e', '窗帘是什么颜色？', '浅蓝色', '黄色', '白色', '绿色', 'A', 'medium'),
('pic_e', '沙发和电视之间放着什么？', '落地灯', '垃圾桶', '绿植', '椅子', 'C', 'medium');
