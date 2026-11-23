-- =====================================================================
-- 教学关系闭环：班级 / 班级学生 / 试卷下发
-- 数据库：ai_marking
-- 幂等：全部使用 CREATE TABLE IF NOT EXISTS，可重复执行
-- 执行：mysql -uroot -p ai_marking < class_module.sql
-- =====================================================================

USE ai_marking;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. class_info 班级表（一个班一位任课教师）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_info (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  class_code  VARCHAR(32)  NOT NULL                COMMENT '班级编码，业务唯一',
  class_name  VARCHAR(64)  NOT NULL                COMMENT '班级名称',
  grade       VARCHAR(32)  NULL                    COMMENT '年级，如 2023',
  teacher_id  BIGINT       NULL                    COMMENT '任课教师，关联 sys_user.id',
  description VARCHAR(512) NULL                    COMMENT '班级简介',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常，0 停用',
  create_time DATETIME     NULL                    COMMENT '创建时间',
  update_time DATETIME     NULL                    COMMENT '更新时间',
  del_flag    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_class_code (class_code),
  KEY idx_class_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

-- ---------------------------------------------------------------------
-- 2. class_student 班级学生关系表（一名学生同时只能在一个班，Service 校验）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_student (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  class_id    BIGINT   NOT NULL                COMMENT '班级ID，关联 class_info.id',
  student_id  BIGINT   NOT NULL                COMMENT '学生ID，关联 sys_user.id',
  status      TINYINT  NOT NULL DEFAULT 1      COMMENT '1 在班，0 停用',
  create_time DATETIME NULL                    COMMENT '加入时间',
  update_time DATETIME NULL                    COMMENT '更新时间',
  del_flag    TINYINT  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，1 已移出',
  PRIMARY KEY (id),
  KEY idx_cs_class (class_id),
  KEY idx_cs_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级学生关系表';

-- ---------------------------------------------------------------------
-- 3. paper_assignment 试卷下发表
--    class_id 非空 = 整班下发；student_id 非空 = 指定学生下发
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paper_assignment (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  paper_id    BIGINT   NOT NULL                COMMENT '试卷ID，关联 exam_paper.id',
  class_id    BIGINT   NULL                    COMMENT '整班下发时的班级ID',
  student_id  BIGINT   NULL                    COMMENT '指定学生下发时的学生ID',
  teacher_id  BIGINT   NULL                    COMMENT '下发操作人',
  assign_time DATETIME NULL                    COMMENT '下发时间',
  deadline    DATETIME NULL                    COMMENT '截止时间，可空',
  status      TINYINT  NOT NULL DEFAULT 1      COMMENT '1 有效，0 撤销',
  create_time DATETIME NULL                    COMMENT '创建时间',
  update_time DATETIME NULL                    COMMENT '更新时间',
  del_flag    TINYINT  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_pa_paper (paper_id),
  KEY idx_pa_class (class_id),
  KEY idx_pa_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷下发表';
