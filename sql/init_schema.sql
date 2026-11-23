-- =====================================================================
-- 智能阅卷系统 数据库初始化脚本（幂等，可重复执行）
-- 数据库：ai_marking  字符集：utf8mb4
-- 作用：
--   1. 补齐全部 13 张业务表（已存在的表会自动补缺失列，不破坏现有数据）；
--   2. 写入基础角色（ADMIN/TEACHER/STUDENT）与默认管理员账号；
--   3. 管理员账号：admin / 123456（密码为与后端 BCryptPasswordEncoder 一致的哈希）。
-- 执行方式：mysql -uroot -p ai_marking < init_schema.sql
-- =====================================================================

USE ai_marking;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 工具存储过程：列不存在才添加（保证脚本可重复执行、不报错）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_col;
DELIMITER //
CREATE PROCEDURE add_col(IN t VARCHAR(64), IN c VARCHAR(64), IN def TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', def);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS add_idx;
DELIMITER //
CREATE PROCEDURE add_idx(IN t VARCHAR(64), IN idx VARCHAR(64), IN cols TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = idx) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD INDEX `', idx, '` (', cols, ')');
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. sys_user 用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
CALL add_col('sys_user','username',     'VARCHAR(50)  NOT NULL');
CALL add_col('sys_user','password',     'VARCHAR(100) NOT NULL');
CALL add_col('sys_user','nickname',     'VARCHAR(64)  NULL');
CALL add_col('sys_user','email',        'VARCHAR(128) NULL');
CALL add_col('sys_user','phone',        'VARCHAR(20)  NULL');
CALL add_col('sys_user','avatar',       'VARCHAR(255) NULL');
CALL add_col('sys_user','status',       'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('sys_user','last_login_time','DATETIME   NULL');
CALL add_col('sys_user','last_login_ip','VARCHAR(64)  NULL');
CALL add_col('sys_user','remark',       'VARCHAR(512) NULL');
CALL add_col('sys_user','create_time',  'DATETIME     NULL');
CALL add_col('sys_user','update_time',  'DATETIME     NULL');
CALL add_col('sys_user','del_flag',     'TINYINT      NOT NULL DEFAULT 0');
CALL add_idx('sys_user','idx_username','username');

-- ---------------------------------------------------------------------
-- 2. sys_role 角色表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';
CALL add_col('sys_role','role_code',   'VARCHAR(32)  NOT NULL');
CALL add_col('sys_role','role_name',   'VARCHAR(64)  NOT NULL');
CALL add_col('sys_role','description', 'VARCHAR(256) NULL');
CALL add_col('sys_role','sort',        'INT          NOT NULL DEFAULT 0');
CALL add_col('sys_role','status',      'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('sys_role','create_time', 'DATETIME     NULL');
CALL add_col('sys_role','update_time', 'DATETIME     NULL');
CALL add_col('sys_role','del_flag',    'TINYINT      NOT NULL DEFAULT 0');
CALL add_idx('sys_role','idx_role_code','role_code');

-- ---------------------------------------------------------------------
-- 3. sys_user_role 用户-角色关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';
CALL add_col('sys_user_role','user_id',    'BIGINT  NOT NULL');
CALL add_col('sys_user_role','role_id',    'BIGINT  NOT NULL');
CALL add_col('sys_user_role','create_time','DATETIME NULL');
CALL add_col('sys_user_role','update_time','DATETIME NULL');
CALL add_col('sys_user_role','del_flag',   'TINYINT NOT NULL DEFAULT 0');
CALL add_idx('sys_user_role','uk_user_role','user_id, role_id');

-- ---------------------------------------------------------------------
-- 4. sys_permission 权限/菜单表（当前前端权限由配置派生，此表预留）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限菜单表';
CALL add_col('sys_permission','parent_id',  'BIGINT       NOT NULL DEFAULT 0');
CALL add_col('sys_permission','perm_code',  'VARCHAR(128) NULL');
CALL add_col('sys_permission','perm_name',  'VARCHAR(64)  NULL');
CALL add_col('sys_permission','perm_type',  'TINYINT      NULL');
CALL add_col('sys_permission','path',       'VARCHAR(255) NULL');
CALL add_col('sys_permission','method',     'VARCHAR(10)  NULL');
CALL add_col('sys_permission','component',  'VARCHAR(255) NULL');
CALL add_col('sys_permission','icon',       'VARCHAR(64)  NULL');
CALL add_col('sys_permission','sort',       'INT          NOT NULL DEFAULT 0');
CALL add_col('sys_permission','visible',    'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('sys_permission','status',     'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('sys_permission','remark',     'VARCHAR(255) NULL');
CALL add_col('sys_permission','create_time','DATETIME     NULL');
CALL add_col('sys_permission','update_time','DATETIME     NULL');
CALL add_col('sys_permission','del_flag',   'TINYINT      NOT NULL DEFAULT 0');
CALL add_idx('sys_permission','idx_perm_code','perm_code');

-- ---------------------------------------------------------------------
-- 5. course 课程表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';
CALL add_col('course','course_code',  'VARCHAR(64)   NULL');
CALL add_col('course','course_name',  'VARCHAR(128)  NOT NULL');
CALL add_col('course','teacher_id',   'BIGINT        NULL');
CALL add_col('course','term',         'VARCHAR(32)   NULL');
CALL add_col('course','credit',       'DECIMAL(4,1)  NULL');
CALL add_col('course','description',  'VARCHAR(1024) NULL');
CALL add_col('course','status',       'TINYINT       NOT NULL DEFAULT 1');
CALL add_col('course','create_time',  'DATETIME      NULL');
CALL add_col('course','update_time',  'DATETIME      NULL');
CALL add_col('course','del_flag',     'TINYINT       NOT NULL DEFAULT 0');
CALL add_idx('course','idx_teacher_id','teacher_id');
CALL add_idx('course','idx_course_code','course_code');

-- ---------------------------------------------------------------------
-- 6. exam_paper 试卷表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_paper (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';
CALL add_col('exam_paper','paper_no',      'VARCHAR(64)   NULL');
CALL add_col('exam_paper','paper_title',   'VARCHAR(200)  NOT NULL');
CALL add_col('exam_paper','course_id',     'BIGINT        NULL');
CALL add_col('exam_paper','answer_mode',   'TINYINT       NOT NULL DEFAULT 1');
CALL add_col('exam_paper','status',        'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('exam_paper','total_score',   'DECIMAL(6,2)  NULL');
CALL add_col('exam_paper','pass_score',    'DECIMAL(6,2)  NULL');
CALL add_col('exam_paper','question_count','INT           NOT NULL DEFAULT 0');
CALL add_col('exam_paper','duration',      'INT           NULL');
CALL add_col('exam_paper','start_time',    'DATETIME      NULL');
CALL add_col('exam_paper','end_time',      'DATETIME      NULL');
CALL add_col('exam_paper','ai_marking',    'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('exam_paper','creator_id',    'BIGINT        NULL');
CALL add_col('exam_paper','description',   'VARCHAR(1024) NULL');
CALL add_col('exam_paper','pdf_url',       'VARCHAR(512)  NULL');
CALL add_col('exam_paper','image_url',     'TEXT          NULL');
CALL add_col('exam_paper','create_time',   'DATETIME      NULL');
CALL add_col('exam_paper','update_time',   'DATETIME      NULL');
CALL add_col('exam_paper','del_flag',      'TINYINT       NOT NULL DEFAULT 0');
CALL add_idx('exam_paper','idx_course_id','course_id');
CALL add_idx('exam_paper','idx_creator_id','creator_id');
CALL add_idx('exam_paper','idx_status','status');
CALL add_idx('exam_paper','idx_paper_no','paper_no');

-- ---------------------------------------------------------------------
-- 7. exam_question 试卷题目表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷题目表';
CALL add_col('exam_question','paper_id',         'BIGINT       NULL');
CALL add_col('exam_question','question_no',      'VARCHAR(32)  NULL');
CALL add_col('exam_question','section_name',     'VARCHAR(64)  NULL');
CALL add_col('exam_question','question_type',    'TINYINT      NULL');
CALL add_col('exam_question','question_content', 'TEXT         NULL');
CALL add_col('exam_question','standard_answer',  'TEXT         NULL');
CALL add_col('exam_question','scoring_rule',     'TEXT         NULL');
CALL add_col('exam_question','score',            'DECIMAL(6,2) NULL');
CALL add_col('exam_question','difficulty',       'TINYINT      NULL');
CALL add_col('exam_question','prompt_template_id','BIGINT      NULL');
CALL add_col('exam_question','sort_order',       'INT          NOT NULL DEFAULT 0');
CALL add_col('exam_question','create_time',      'DATETIME     NULL');
CALL add_col('exam_question','update_time',      'DATETIME     NULL');
CALL add_col('exam_question','del_flag',         'TINYINT      NOT NULL DEFAULT 0');
CALL add_idx('exam_question','idx_paper_id','paper_id');
CALL add_idx('exam_question','idx_prompt_template_id','prompt_template_id');

-- ---------------------------------------------------------------------
-- 8. prompt_template AI 评分提示词模板表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI评分提示词模板表';
CALL add_col('prompt_template','template_code','VARCHAR(64)  NULL');
CALL add_col('prompt_template','template_name','VARCHAR(128) NOT NULL');
CALL add_col('prompt_template','question_type','TINYINT      NOT NULL DEFAULT 0');
CALL add_col('prompt_template','course_id',    'BIGINT       NULL');
CALL add_col('prompt_template','system_prompt','TEXT         NULL');
CALL add_col('prompt_template','user_prompt',  'TEXT         NULL');
CALL add_col('prompt_template','model_name',   'VARCHAR(64)  NULL');
CALL add_col('prompt_template','temperature',  'DECIMAL(3,2) NULL');
CALL add_col('prompt_template','version',      'INT          NOT NULL DEFAULT 1');
CALL add_col('prompt_template','status',       'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('prompt_template','remark',       'VARCHAR(512) NULL');
CALL add_col('prompt_template','create_time',  'DATETIME     NULL');
CALL add_col('prompt_template','update_time',  'DATETIME     NULL');
CALL add_col('prompt_template','del_flag',     'TINYINT      NOT NULL DEFAULT 0');
CALL add_idx('prompt_template','idx_question_type','question_type');
CALL add_idx('prompt_template','idx_course_id','course_id');
CALL add_idx('prompt_template','idx_template_code','template_code');

-- ---------------------------------------------------------------------
-- 9. question_knowledge 知识点表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_knowledge (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';
CALL add_col('question_knowledge','knowledge_code','VARCHAR(64)  NULL');
CALL add_col('question_knowledge','knowledge_name','VARCHAR(128) NOT NULL');
CALL add_col('question_knowledge','course_id',     'BIGINT       NULL');
CALL add_col('question_knowledge','parent_id',     'BIGINT       NOT NULL DEFAULT 0');
CALL add_col('question_knowledge','level',         'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('question_knowledge','description',   'VARCHAR(512) NULL');
CALL add_col('question_knowledge','sort',          'INT          NOT NULL DEFAULT 0');
CALL add_col('question_knowledge','status',        'TINYINT      NOT NULL DEFAULT 1');
CALL add_col('question_knowledge','create_time',   'DATETIME     NULL');
CALL add_col('question_knowledge','update_time',   'DATETIME     NULL');
CALL add_col('question_knowledge','del_flag',      'TINYINT      NOT NULL DEFAULT 0');
CALL add_idx('question_knowledge','idx_course_id','course_id');
CALL add_idx('question_knowledge','idx_parent_id','parent_id');

-- ---------------------------------------------------------------------
-- 10. question_knowledge_rel 题目-知识点关联表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_knowledge_rel (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-知识点关联表';
CALL add_col('question_knowledge_rel','question_id', 'BIGINT   NOT NULL');
CALL add_col('question_knowledge_rel','knowledge_id','BIGINT   NOT NULL');
CALL add_col('question_knowledge_rel','create_time', 'DATETIME NULL');
CALL add_col('question_knowledge_rel','update_time', 'DATETIME NULL');
CALL add_col('question_knowledge_rel','del_flag',    'TINYINT  NOT NULL DEFAULT 0');
CALL add_idx('question_knowledge_rel','idx_question_id','question_id');
CALL add_idx('question_knowledge_rel','idx_knowledge_id','knowledge_id');

-- ---------------------------------------------------------------------
-- 11. exam_answer 答卷表（线上作答 + 纸质扫描件统一在此表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_answer (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答卷表';
CALL add_col('exam_answer','paper_id',          'BIGINT        NULL');
CALL add_col('exam_answer','student_id',        'BIGINT        NULL');
CALL add_col('exam_answer','student_no',        'VARCHAR(32)   NULL');
CALL add_col('exam_answer','student_name',      'VARCHAR(64)   NULL');
CALL add_col('exam_answer','student_class',     'VARCHAR(64)   NULL');
CALL add_col('exam_answer','answer_source',     'VARCHAR(20)   NULL');
CALL add_col('exam_answer','import_batch_no',   'VARCHAR(64)   NULL');
CALL add_col('exam_answer','answer_content',    'LONGTEXT      NULL');
CALL add_col('exam_answer','paper_img_url',     'VARCHAR(512)  NULL');
CALL add_col('exam_answer','ocr_text',          'LONGTEXT      NULL');
CALL add_col('exam_answer','sim_hash',          'VARCHAR(32)   NULL');
CALL add_col('exam_answer','ai_reference_score','DECIMAL(6,2)  NULL');
CALL add_col('exam_answer','teacher_final_score','DECIMAL(6,2) NULL');
CALL add_col('exam_answer','score_diff',        'DECIMAL(6,2)  NULL');
CALL add_col('exam_answer','score_modified',    'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('exam_answer','marking_status',    'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('exam_answer','marking_error',     'VARCHAR(1024) NULL');
CALL add_col('exam_answer','review_status',     'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('exam_answer','reviewer_id',       'BIGINT        NULL');
CALL add_col('exam_answer','reviewer_name',     'VARCHAR(64)   NULL');
CALL add_col('exam_answer','review_remark',     'VARCHAR(1024) NULL');
CALL add_col('exam_answer','review_time',       'DATETIME      NULL');
CALL add_col('exam_answer','submit_time',       'DATETIME      NULL');
CALL add_col('exam_answer','mark_time',         'DATETIME      NULL');
CALL add_col('exam_answer','create_time',       'DATETIME      NULL');
CALL add_col('exam_answer','update_time',       'DATETIME      NULL');
CALL add_col('exam_answer','del_flag',          'TINYINT       NOT NULL DEFAULT 0');
CALL add_idx('exam_answer','idx_paper_id','paper_id');
CALL add_idx('exam_answer','idx_student_id','student_id');
CALL add_idx('exam_answer','idx_student_no','student_no');
CALL add_idx('exam_answer','idx_marking_status','marking_status');
CALL add_idx('exam_answer','idx_review_status','review_status');
CALL add_idx('exam_answer','idx_answer_source','answer_source');
CALL add_idx('exam_answer','idx_import_batch_no','import_batch_no');

-- ---------------------------------------------------------------------
-- 12. mark_task 异步阅卷/OCR 任务表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mark_task (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步阅卷/OCR任务表';
CALL add_col('mark_task','task_no',     'VARCHAR(64)   NOT NULL');
CALL add_col('mark_task','answer_id',   'BIGINT        NULL');
CALL add_col('mark_task','task_type',   'VARCHAR(16)   NULL');
CALL add_col('mark_task','status',      'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('mark_task','retry_count', 'INT           NOT NULL DEFAULT 0');
CALL add_col('mark_task','error_msg',   'VARCHAR(1024) NULL');
CALL add_col('mark_task','start_time',  'DATETIME      NULL');
CALL add_col('mark_task','finish_time', 'DATETIME      NULL');
CALL add_col('mark_task','create_time', 'DATETIME      NULL');
CALL add_col('mark_task','update_time', 'DATETIME      NULL');
CALL add_col('mark_task','del_flag',    'TINYINT       NOT NULL DEFAULT 0');
CALL add_idx('mark_task','uk_task_no','task_no');
CALL add_idx('mark_task','idx_answer_id','answer_id');
CALL add_idx('mark_task','idx_status','status');

-- ---------------------------------------------------------------------
-- 13. learning_analysis_report 学情分析报告表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_analysis_report (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学情分析报告表';
CALL add_col('learning_analysis_report','paper_id',     'BIGINT        NULL');
CALL add_col('learning_analysis_report','course_id',    'BIGINT        NULL');
CALL add_col('learning_analysis_report','report_type',  'VARCHAR(16)   NULL');
CALL add_col('learning_analysis_report','report_title', 'VARCHAR(200)  NULL');
CALL add_col('learning_analysis_report','analysis_text','LONGTEXT      NULL');
CALL add_col('learning_analysis_report','ppt_file_url', 'VARCHAR(512)  NULL');
CALL add_col('learning_analysis_report','status',       'TINYINT       NOT NULL DEFAULT 0');
CALL add_col('learning_analysis_report','error_msg',    'VARCHAR(1024) NULL');
CALL add_col('learning_analysis_report','creator_id',   'BIGINT        NULL');
CALL add_col('learning_analysis_report','create_time',  'DATETIME      NULL');
CALL add_col('learning_analysis_report','update_time',  'DATETIME      NULL');
CALL add_col('learning_analysis_report','del_flag',     'TINYINT       NOT NULL DEFAULT 0');
CALL add_idx('learning_analysis_report','idx_paper_id','paper_id');
CALL add_idx('learning_analysis_report','idx_creator_id','creator_id');

-- ---------------------------------------------------------------------
-- 14. exam_answer_item 答卷逐题 AI / 教师结果表
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

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS add_col;
DROP PROCEDURE IF EXISTS add_idx;

-- =====================================================================
-- 种子数据（幂等：先按业务键清理再插入，可重复执行）
-- =====================================================================

-- 基础角色
DELETE FROM sys_role WHERE role_code IN ('ADMIN','TEACHER','STUDENT');
INSERT INTO sys_role (id, role_code, role_name, description, sort, status, create_time, update_time, del_flag) VALUES
  (1, 'ADMIN',   '管理员', '拥有系统全部权限',            1, 1, NOW(), NOW(), 0),
  (2, 'TEACHER', '教师',   '课程/试卷/阅卷/成绩/学情管理', 2, 1, NOW(), NOW(), 0),
  (3, 'STUDENT', '学生',   '在线作答与成绩查看',          3, 1, NOW(), NOW(), 0);

-- 默认管理员 admin / 123456（密码哈希与后端 BCryptPasswordEncoder 一致，已校验 matches=true）
DELETE FROM sys_user WHERE username = 'admin';
INSERT INTO sys_user (id, username, password, nickname, email, status, create_time, update_time, del_flag) VALUES
  (1, 'admin', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '系统管理员', 'admin@example.com', 1, NOW(), NOW(), 0);

-- 绑定管理员角色
DELETE FROM sys_user_role WHERE user_id = 1;
INSERT INTO sys_user_role (id, user_id, role_id, create_time) VALUES (1, 1, 1, NOW());

-- 让自增 ID 从大于种子值开始，避免后续插入主键冲突
ALTER TABLE sys_user AUTO_INCREMENT = 100;
ALTER TABLE sys_role AUTO_INCREMENT = 100;
ALTER TABLE sys_user_role AUTO_INCREMENT = 100;
