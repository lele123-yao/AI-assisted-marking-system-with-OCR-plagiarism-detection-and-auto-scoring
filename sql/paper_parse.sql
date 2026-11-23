-- =====================================================================
-- 试卷上传解析 + AI 逐题评语改造（幂等，可重复执行）
-- 作用：
--   1. exam_paper 增加试卷源文件字段 pdf_url / image_url；
--   2. 新建 exam_answer_item，逐题保存 AI 参考分/评语与教师最终分/评语。
-- 说明：answer_content 保持原有语义，不在此脚本中改动，避免影响学情统计。
-- 执行方式：mysql -uroot -p ai_marking -e "source d:/ai-marking-system/sql/paper_parse.sql"
-- =====================================================================

USE ai_marking;
SET NAMES utf8mb4;

-- 列不存在才添加，保证脚本可重复执行
DROP PROCEDURE IF EXISTS pp_add_col;
DELIMITER //
CREATE PROCEDURE pp_add_col(IN t VARCHAR(64), IN c VARCHAR(64), IN def TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', def);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. 试卷源文件：只存 MinIO 对象名，展示时再换签名地址
-- ---------------------------------------------------------------------
CALL pp_add_col('exam_paper', 'pdf_url',
  'VARCHAR(512) NULL COMMENT ''PDF 对象名'' AFTER description');
CALL pp_add_col('exam_paper', 'image_url',
  'TEXT NULL COMMENT ''拍照图片对象名 JSON 数组'' AFTER pdf_url');

-- ---------------------------------------------------------------------
-- 2. 答卷逐题结果：AI 与教师分列存储，互不覆盖
--    - ai_score / ai_comment      ：AI 异步阅卷写入，重试可覆盖
--    - final_score / final_comment：教师人工复核写入，只在教师提交时更新
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_answer_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer_id BIGINT NOT NULL COMMENT '答卷ID',
  question_id BIGINT NOT NULL COMMENT '题目ID',
  ai_score DECIMAL(6,2) NULL COMMENT 'AI 参考分',
  ai_comment VARCHAR(1000) NULL COMMENT 'AI 评语',
  final_score DECIMAL(6,2) NULL COMMENT '教师最终分',
  final_comment VARCHAR(1000) NULL COMMENT '教师最终评语',
  create_time DATETIME NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_answer_question (answer_id, question_id),
  KEY idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答卷逐题 AI 与教师复核结果';

DROP PROCEDURE IF EXISTS pp_add_col;
