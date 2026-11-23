-- ============================================================
-- 联调演示种子数据（Mock Data）
--
-- 覆盖前端 25 个业务页面的列表、统计、图表与详情数据：
--   课程 / 班级 / 题目 / 提示词模板 / 试卷
--   答卷列表 / 在线作答 / 答卷详情 / 考生名单 / 图片上传 / 教师录分
--   阅卷详情 / 抄袭比对 / 抄袭报告 / 成绩复核 / 成绩统计 / 成绩导出
--   学情报表 / 知识点掌握 / AI 讲评建议 / 讲评 PPT
--   用户管理 / 角色管理 / 学生端我的试卷 / 我的成绩
--
-- 演示账号（密码统一 123456，与 admin 相同的 BCrypt 哈希）：
--   admin / 123456            管理员
--   demo_student / 123456     学生（已下发 3 份试卷，其中 2 份已出分）
--   mock_stu01 ~ mock_stu15   学生（用于成绩分布、名单、抄袭比对）
--
-- 数据均使用 9001+ 的保留 ID，可反复执行本脚本（脚本会先清理旧 Mock 数据）。
-- 如需彻底移除演示数据，执行文件末尾的清理 SQL。
-- ============================================================
USE ai_marking;

SET NAMES utf8mb4;

-- ---------- 清理旧 Mock 数据（按依赖顺序） ----------
DELETE FROM question_knowledge_rel WHERE question_id BETWEEN 9001 AND 9099 OR knowledge_id BETWEEN 9001 AND 9099;
DELETE FROM learning_analysis_report WHERE id BETWEEN 9001 AND 9099;
DELETE FROM exam_answer WHERE id BETWEEN 9001 AND 9099;
DELETE FROM exam_question WHERE id BETWEEN 9001 AND 9099;
DELETE FROM paper_assignment WHERE id BETWEEN 9001 AND 9099;
DELETE FROM class_student WHERE id BETWEEN 9001 AND 9099;
DELETE FROM class_info WHERE id BETWEEN 9001 AND 9099;
DELETE FROM exam_paper WHERE id BETWEEN 9001 AND 9099;
DELETE FROM question_knowledge WHERE id BETWEEN 9001 AND 9099;
DELETE FROM prompt_template WHERE id BETWEEN 9001 AND 9099;
DELETE FROM sys_user_role WHERE user_id BETWEEN 9001 AND 9099;
DELETE FROM sys_user WHERE id BETWEEN 9001 AND 9099;
DELETE FROM course WHERE id BETWEEN 9001 AND 9099;

-- ---------- 课程 ----------
INSERT INTO course (id, course_code, course_name, teacher_id, term, credit, description, status, create_time, update_time, del_flag) VALUES
(9001, 'OS301', '操作系统', 103, '2026-2027-1', 3.0, '进程管理、内存管理与文件系统', 1, NOW(), NOW(), 0),
(9002, 'NET201', '计算机网络', 103, '2026-2027-1', 2.5, 'TCP/IP 协议栈与网络编程基础（本学期暂停开课）', 0, NOW(), NOW(), 0);

-- ---------- 班级 ----------
INSERT INTO class_info (id, class_code, class_name, grade, teacher_id, description, status, create_time, update_time, del_flag) VALUES
(9001, 'CS-2023-2', '计算机2023级2班', '2023', 103, '数据结构与算法课程教学班', 1, NOW(), NOW(), 0),
(9002, 'SE-2023-1', '软件工程2023级1班', '2023', 103, '软件工程专业教学班', 1, NOW(), NOW(), 0),
(9003, 'CS-2022-1', '计算机2022级1班', '2022', 1, '已毕业班级，仅用于状态演示', 0, NOW(), NOW(), 0);

-- ---------- 学生账号（密码均为 123456） ----------
INSERT INTO sys_user (id, username, password, nickname, email, phone, avatar, status, last_login_time, last_login_ip, remark, create_time, update_time, del_flag) VALUES
(9001, 'demo_student', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '演示学生', 'demo.student@example.com', '13800000001', NULL, 1, NULL, NULL, '联调演示账号', NOW(), NOW(), 0),
(9002, 'mock_stu01', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '李思远', 'stu01@example.com', '13800000002', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9003, 'mock_stu02', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '王子涵', 'stu02@example.com', '13800000003', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9004, 'mock_stu03', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '张雨桐', 'stu03@example.com', '13800000004', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9005, 'mock_stu04', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '陈嘉豪', 'stu04@example.com', '13800000005', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9006, 'mock_stu05', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '刘子墨', 'stu05@example.com', '13800000006', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9007, 'mock_stu06', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '赵一诺', 'stu06@example.com', '13800000007', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9008, 'mock_stu07', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '孙浩然', 'stu07@example.com', '13800000008', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9009, 'mock_stu08', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '周可欣', 'stu08@example.com', '13800000009', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9010, 'mock_stu09', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '吴俊杰', 'stu09@example.com', '13800000010', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9011, 'mock_stu10', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '郑欣怡', 'stu10@example.com', '13800000011', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9012, 'mock_stu11', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '冯宇航', 'stu11@example.com', '13800000012', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9013, 'mock_stu12', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '黄诗涵', 'stu12@example.com', '13800000013', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9014, 'mock_stu13', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '徐子豪', 'stu13@example.com', '13800000014', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9015, 'mock_stu14', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '马雨欣', 'stu14@example.com', '13800000015', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0),
(9016, 'mock_stu15', '$2a$10$2X6QkbhY7iG6il.now5.B.UiauAvCPLyjjXo8PrSEzGoBJQsF7iL.', '林嘉诚', 'stu15@example.com', '13800000016', NULL, 1, NULL, NULL, '联调演示学生', NOW(), NOW(), 0);

INSERT INTO sys_user_role (user_id, role_id, create_time, update_time, del_flag)
SELECT id, 3, NOW(), NOW(), 0 FROM sys_user WHERE id BETWEEN 9001 AND 9016;

-- ---------- 班级成员 ----------
INSERT INTO class_student (id, class_id, student_id, status, create_time, update_time, del_flag) VALUES
(9001, 9001, 9001, 1, NOW(), NOW(), 0),
(9002, 9001, 9002, 1, NOW(), NOW(), 0),
(9003, 9001, 9003, 1, NOW(), NOW(), 0),
(9004, 9001, 9004, 1, NOW(), NOW(), 0),
(9005, 9001, 9005, 1, NOW(), NOW(), 0),
(9006, 9001, 9006, 1, NOW(), NOW(), 0),
(9007, 9001, 9007, 1, NOW(), NOW(), 0),
(9008, 9001, 9008, 1, NOW(), NOW(), 0),
(9009, 9002, 9009, 1, NOW(), NOW(), 0),
(9010, 9002, 9010, 1, NOW(), NOW(), 0),
(9011, 9002, 9011, 1, NOW(), NOW(), 0),
(9012, 9002, 9012, 1, NOW(), NOW(), 0),
(9013, 9002, 9013, 1, NOW(), NOW(), 0),
(9014, 9002, 9014, 1, NOW(), NOW(), 0),
(9015, 9002, 9015, 1, NOW(), NOW(), 0),
(9016, 9002, 9016, 1, NOW(), NOW(), 0);

-- ---------- 试卷 ----------
INSERT INTO exam_paper (id, paper_no, paper_title, course_id, answer_mode, status, total_score, pass_score, question_count, duration, start_time, end_time, ai_marking, creator_id, description, create_time, update_time, del_flag) VALUES
(9001, 'MOCK-DS-2026-FINAL', '数据结构与算法 期末模拟卷', 1, 2, 2, 100.00, 60.00, 5, NULL, NULL, NULL, 1, 1, '纸质扫描阅卷，覆盖 16 名学生的多种阅卷状态', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9002, 'MOCK-DS-2026-QUIZ', '数据结构与算法 单元自测（线上）', 1, 1, 1, 50.00, 30.00, 3, 90, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, 1, '学生端在线作答演示试卷，尚未提交', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9003, 'MOCK-MA-2026-MID', '高等数学（上）期中模拟', 2, 1, 2, 100.00, 60.00, 3, 120, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 1, 1, '线上作答且已完成 AI 批改，用于成绩统计与学情分析', DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),
(9004, 'MOCK-DS-2025-FINAL', '数据结构 2025 期末真题（归档）', 1, 2, 3, 50.00, 30.00, 2, NULL, NULL, NULL, 1, 1, '已归档试卷，用于成绩导出与归档状态演示', DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0);

-- ---------- 题目 ----------
INSERT INTO exam_question (id, paper_id, question_no, section_name, question_type, question_content, standard_answer, scoring_rule, score, difficulty, prompt_template_id, sort_order, create_time, update_time, del_flag) VALUES
(9001, 9001, '1', '一、简答与分析', 5, '简述栈和队列的主要区别，并各举一个典型应用场景。', '栈后进先出，队列先进先出；栈可用于函数调用与表达式求值，队列可用于任务调度与广度优先遍历。', '答出区别得 10 分，两个应用场景各 5 分。', 20.00, 0.40, NULL, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9002, 9001, '2', '一、简答与分析', 5, '解释哈希表冲突产生的原因，并说明两种常用的冲突解决方法。', '不同关键字经哈希函数映射到同一地址即产生冲突；常用链地址法与开放定址法。', '原因 8 分，两种方法各 6 分。', 20.00, 0.45, NULL, 2, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9003, 9001, '3', '二、综合论述', 6, '比较快速排序与归并排序在时间复杂度、空间复杂度和稳定性方面的差异。', '快排平均 O(nlogn)、最坏 O(n2)、空间 O(logn)、不稳定；归并稳定 O(nlogn)、空间 O(n)、稳定。', '三个维度各 6 分，结论 2 分。', 20.00, 0.60, NULL, 3, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9004, 9001, '4', '二、综合论述', 7, '给定关键字序列 45, 24, 53, 12, 37, 68，构造二叉排序树，并写出中序遍历结果。', '中序遍历结果为 12, 24, 37, 45, 53, 68。', '建树 12 分，中序遍历 8 分。', 20.00, 0.55, NULL, 4, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9005, 9001, '5', '三、案例分析', 8, '某电商系统订单查询响应缓慢，请从数据库索引、缓存与 SQL 优化角度给出方案。', '建立组合索引，避免全表扫描；引入 Redis 缓存热点订单；分页查询并避免 SELECT 星号。', '问题分析 6 分，优化方案 14 分。', 20.00, 0.65, NULL, 5, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9006, 9002, '1', '一、基础题', 5, '请说明线性表顺序存储与链式存储的优缺点。', '顺序存储随机访问快但插入删除代价高；链式存储插入删除方便但需额外指针空间。', '优点 8 分，缺点 7 分。', 15.00, 0.40, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9007, 9002, '2', '一、基础题', 5, '简述二叉树的先序、中序、后序遍历规则。', '先序根左右，中序左根右，后序左右根。', '每种遍历 5 分。', 15.00, 0.35, NULL, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9008, 9002, '3', '二、综合题', 6, '论述图的深度优先遍历与广度优先遍历的区别及适用场景。', 'DFS 借助栈、适合路径搜索；BFS 借助队列、适合最短路径与层次遍历。', '区别 8 分，场景 12 分。', 20.00, 0.55, NULL, 3, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9009, 9003, '1', '一、计算题', 7, '求函数 f(x)=x^3-3x^2+2 的单调区间与极值。', 'f(x) 在 (-∞,0) 与 (2,+∞) 单调递增，在 (0,2) 单调递减；极大值 f(0)=2，极小值 f(2)=-2。', '求导 10 分，单调性 10 分，极值 10 分。', 30.00, 0.55, NULL, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),
(9010, 9003, '2', '一、计算题', 7, '计算定积分 ∫(0 到 1) x·e^x dx。', '原式 = [x·e^x - e^x] 在 0 到 1 的取值，结果为 1。', '分部积分 20 分，结果 10 分。', 30.00, 0.60, NULL, 2, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),
(9011, 9003, '3', '二、证明题', 6, '叙述并证明拉格朗日中值定理。', '若 f(x) 在 [a,b] 连续、(a,b) 可导，则存在 ξ 使 f(b)-f(a)=f′(ξ)(b-a)。', '条件 10 分，构造辅助函数 15 分，证明 15 分。', 40.00, 0.70, NULL, 3, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),
(9012, 9004, '1', '一、简答题', 5, '简述堆排序的基本思想。', '构建大顶堆，反复交换堆顶与末尾元素并调整堆。', '建堆 13 分，排序过程 12 分。', 25.00, 0.50, NULL, 1, DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0),
(9013, 9004, '2', '二、计算题', 7, '对序列 49, 38, 65, 97, 76 进行直接插入排序，写出每一趟结果。', '依次得到 38,49,65,97,76；38,49,65,97,76；38,49,65,76,97。', '每趟 5 分。', 25.00, 0.45, NULL, 2, DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0);

-- ---------- 知识点（高等数学，与已有数据结构知识点 1-5 区分） ----------
INSERT INTO question_knowledge (id, knowledge_code, knowledge_name, course_id, parent_id, level, description, sort, status, create_time, update_time, del_flag) VALUES
(9001, 'MA01', '函数与极限', 2, 0, 1, '函数性质、极限计算与连续性', 1, 1, NOW(), NOW(), 0),
(9002, 'MA02', '导数与微分', 2, 0, 1, '求导法则与微分应用', 2, 1, NOW(), NOW(), 0),
(9003, 'MA03', '微分中值定理', 2, 9002, 2, '罗尔、拉格朗日与柯西中值定理', 3, 1, NOW(), NOW(), 0);

INSERT INTO question_knowledge_rel (id, question_id, knowledge_id, create_time, update_time, del_flag) VALUES
(9001, 9001, 1, NOW(), NOW(), 0),
(9002, 9001, 5, NOW(), NOW(), 0),
(9003, 9002, 5, NOW(), NOW(), 0),
(9004, 9003, 4, NOW(), NOW(), 0),
(9005, 9004, 2, NOW(), NOW(), 0),
(9006, 9005, 5, NOW(), NOW(), 0),
(9007, 9006, 1, NOW(), NOW(), 0),
(9008, 9007, 5, NOW(), NOW(), 0),
(9009, 9008, 4, NOW(), NOW(), 0),
(9010, 9009, 9001, NOW(), NOW(), 0),
(9011, 9010, 9002, NOW(), NOW(), 0),
(9012, 9011, 9003, NOW(), NOW(), 0),
(9013, 9012, 1, NOW(), NOW(), 0),
(9014, 9013, 4, NOW(), NOW(), 0);

-- ---------- 试卷下发（学生端「我的试卷」数据） ----------
INSERT INTO paper_assignment (id, paper_id, class_id, student_id, teacher_id, assign_time, deadline, status, create_time, update_time, del_flag) VALUES
(9001, 9001, 9001, NULL, 103, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(9002, 9001, 9002, NULL, 103, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(9003, 9002, 9001, NULL, 103, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9004, 9002, NULL, 9001, 103, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9005, 9003, 9001, NULL, 103, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),
(9006, 9004, 9002, NULL, 103, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 1, DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0);

-- ---------- 纸质答卷（试卷 9001，覆盖全部阅卷状态） ----------
-- answer_content 为逐题得分明细：[{"questionId":...,"score":...}]
INSERT INTO exam_answer (id, paper_id, student_id, student_no, student_name, student_class, answer_source, import_batch_no, answer_content, paper_img_url, ocr_text, ai_reference_score, teacher_final_score, score_diff, score_modified, marking_status, marking_error, review_status, reviewer_id, reviewer_name, review_remark, review_time, submit_time, mark_time, create_time, update_time, del_flag, sim_hash) VALUES
(9001, 9001, 9001, '20230101', '演示学生', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":18},{"questionId":9002,"score":17},{"questionId":9003,"score":16},{"questionId":9004,"score":17},{"questionId":9005,"score":18}]',
 NULL,
 '栈是后进先出的线性结构，只允许在栈顶插入和删除，典型应用是函数调用栈和表达式求值；队列是先进先出，队尾入队、队头出队，典型应用是任务调度和广度优先遍历。哈希冲突源于不同关键字映射到同一地址，常用链地址法和开放定址法解决。快速排序平均时间复杂度为O(nlogn)，最坏为O(n2)，空间复杂度O(logn)，不稳定；归并排序时间复杂度稳定为O(nlogn)，空间复杂度O(n)，稳定。二叉排序树的中序遍历结果是有序序列。订单查询优化可以从索引、缓存、分库分表和SQL改写几个方面入手。',
 82.00, 86.00, 4.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后上调分数', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9002, 9001, 9002, '20230102', '李思远', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":15},{"questionId":9002,"score":14},{"questionId":9003,"score":15},{"questionId":9004,"score":15},{"questionId":9005,"score":15}]',
 NULL,
 '栈的特点是后进先出，队列的特点是先进先出，两者都可以用数组或链表实现。哈希冲突是指不同关键字计算出相同的哈希地址，解决方法包括链地址法、开放定址法和再哈希法。快速排序采用分治思想，平均时间复杂度O(nlogn)，最坏O(n2)；归并排序需要额外数组，时间复杂度稳定在O(nlogn)且稳定。二叉排序树中序遍历有序。电商订单查询优化应建立组合索引、避免SELECT星号、使用缓存并考虑读写分离。',
 78.00, 74.00, -4.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9003, 9001, 9003, '20230103', '王子涵', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":19},{"questionId":9002,"score":18},{"questionId":9003,"score":18},{"questionId":9004,"score":18},{"questionId":9005,"score":19}]',
 NULL,
 '栈和队列都是线性表。栈只能在栈顶操作，先进后出，用于递归和括号匹配；队列在队尾插入、队头删除，先进先出，用于打印任务和层次遍历。哈希表通过哈希函数把关键字映射到数组下标，冲突可以用拉链法或线性探测法处理。快速排序和归并排序的平均复杂度都是O(nlogn)，但前者不稳定、额外空间小，后者稳定、需要O(n)辅助空间。中序遍历二叉排序树可以得到递增序列。订单查询慢通常是缺少合适索引或返回数据过多，可以加联合索引、分页查询并引入缓存。',
 90.00, 92.00, 2.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后上调分数', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9004, 9001, 9004, '20230104', '张雨桐', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":14},{"questionId":9002,"score":14},{"questionId":9003,"score":13},{"questionId":9004,"score":13},{"questionId":9005,"score":14}]',
 NULL,
 '栈是后进先出的结构，队列是先进先出的结构。栈适合表达式求值，队列适合任务排队。哈希冲突是不同键得到相同地址，可以用链地址法解决。快速排序平均O(nlogn)，归并排序稳定O(nlogn)。二叉排序树中序遍历递增。订单查询可以从索引和缓存两方面优化。',
 70.00, 68.00, -2.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9005, 9001, 9005, '20230105', '陈嘉豪', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":11},{"questionId":9002,"score":11},{"questionId":9003,"score":11},{"questionId":9004,"score":11},{"questionId":9005,"score":11}]',
 NULL,
 '栈是先进后出，队列是先进先出。哈希冲突可以用拉链法处理。快排平均时间复杂度是O(nlogn)，归并排序也是O(nlogn)。二叉排序树中序遍历得到有序序列。订单查询可以通过加索引和缓存来优化。',
 57.00, 55.00, -2.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9006, 9001, 9006, '20230106', '刘子墨', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":16},{"questionId":9002,"score":16},{"questionId":9003,"score":15},{"questionId":9004,"score":16},{"questionId":9005,"score":16}]',
 NULL,
 '栈是后进先出的线性结构，只允许在栈顶插入和删除，典型应用是函数调用栈和表达式求值；队列是先进先出，队尾入队、队头出队，典型应用是任务调度和广度优先遍历。哈希冲突源于不同关键字映射到同一地址，常用链地址法和开放定址法解决。快速排序平均时间复杂度为O(nlogn)，最坏为O(n2)，空间复杂度O(logn)，不稳定；归并排序时间复杂度稳定为O(nlogn)，空间复杂度O(n)，稳定。二叉排序树的中序遍历结果是有序序列。订单查询优化可以从索引、缓存、分库分表和SQL改写几个方面入手。',
 82.00, 79.00, -3.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9007, 9001, 9007, '20230107', '赵一诺', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":17},{"questionId":9002,"score":17},{"questionId":9003,"score":17},{"questionId":9004,"score":17},{"questionId":9005,"score":17}]',
 NULL,
 '栈和队列都是线性表。栈只能在栈顶操作，先进后出，用于递归和括号匹配；队列在队尾插入、队头删除，先进先出，用于打印任务和层次遍历。哈希表通过哈希函数把关键字映射到数组下标，冲突可以用拉链法或线性探测法处理。快速排序和归并排序的平均复杂度都是O(nlogn)，但前者不稳定、额外空间小，后者稳定、需要O(n)辅助空间。中序遍历二叉排序树可以得到递增序列。订单查询慢通常是缺少合适索引或返回数据过多，可以加联合索引、分页查询并引入缓存。',
 85.00, 85.00, 0.00, 0, 5, NULL, 1, 1, '系统管理员', '复核通过', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9008, 9001, 9008, '20230108', '孙浩然', '计算机2023级2班', 'PAPER_SCAN', 'MOCK-20261118-001',
 '[{"questionId":9001,"score":13},{"questionId":9002,"score":13},{"questionId":9003,"score":13},{"questionId":9004,"score":12},{"questionId":9005,"score":13}]',
 NULL,
 '栈是先进后出，队列是先进先出。哈希冲突可以用拉链法处理。快排平均时间复杂度是O(nlogn)，归并排序也是O(nlogn)。二叉排序树中序遍历得到有序序列。订单查询可以通过加索引和缓存来优化。',
 64.00, 64.00, 0.00, 0, 5, NULL, 1, 1, '系统管理员', '复核通过', DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9009, 9001, 9009, '20230109', '周可欣', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 '[{"questionId":9001,"score":12},{"questionId":9002,"score":13},{"questionId":9003,"score":12},{"questionId":9004,"score":13},{"questionId":9005,"score":13}]',
 NULL,
 '栈是后进先出，队列是先进先出。栈用于递归调用，队列用于任务调度。哈希冲突是不同关键字得到同一地址，可用链地址法解决。快速排序平均O(nlogn)，归并排序稳定O(nlogn)。二叉排序树中序遍历有序。订单查询可从索引、缓存、SQL三方面优化。',
 63.00, NULL, NULL, 0, 3, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9010, 9001, 9010, '20230110', '吴俊杰', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 '[{"questionId":9001,"score":11},{"questionId":9002,"score":12},{"questionId":9003,"score":11},{"questionId":9004,"score":12},{"questionId":9005,"score":12}]',
 NULL,
 '栈后进先出，队列先进先出。哈希冲突用拉链法解决。快排平均O(nlogn)，归并稳定O(nlogn)。二叉排序树中序遍历有序。订单查询可以通过索引和缓存优化。',
 58.00, NULL, NULL, 0, 3, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9011, 9001, 9011, '20230111', '郑欣怡', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 '[{"questionId":9001,"score":16},{"questionId":9002,"score":16},{"questionId":9003,"score":16},{"questionId":9004,"score":16},{"questionId":9005,"score":17}]',
 NULL,
 '栈是后进先出的线性结构，常用于函数调用；队列先进先出，常用于任务调度。哈希冲突源于不同关键字映射到同一地址，可用链地址法和开放定址法解决。快速排序平均与归并排序均为O(nlogn)，但归并排序稳定。二叉排序树中序遍历有序。订单查询可通过组合索引、缓存与分页优化。',
 81.00, NULL, NULL, 0, 3, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9012, 9001, 9012, '20230112', '冯宇航', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 NULL, NULL,
 '栈是后进先出，队列是先进先出。哈希冲突可以用链地址法解决。快速排序平均复杂度O(nlogn)。二叉排序树中序遍历有序。订单查询可以加索引优化。',
 NULL, NULL, NULL, 0, 2, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9013, 9001, 9013, '20230113', '黄诗涵', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 NULL, NULL,
 '栈后进先出，队列先进先出。哈希冲突是不同键映射到同一地址。快排和归并都是O(nlogn)。二叉排序树中序遍历得到有序序列。订单查询可以通过缓存优化。',
 NULL, NULL, NULL, 0, 2, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9014, 9001, 9014, '20230114', '徐子豪', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 NULL, NULL,
 '栈和队列都是线性结构，栈后进先出，队列先进先出。哈希冲突可以用拉链法解决。',
 NULL, NULL, NULL, 0, 1, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9015, 9001, 9015, '20230115', '马雨欣', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-002',
 NULL, NULL,
 NULL, NULL, NULL, NULL, 0, 0, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL),
(9016, 9001, 9016, '20230116', '林嘉诚', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261118-003',
 NULL, NULL,
 NULL, NULL, NULL, NULL, 0, 4, 'OCR识别失败：扫描图片模糊，请重新上传', 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0, NULL);

-- ---------- 线上答卷（试卷 9003，用于学生端成绩与学情分析） ----------
INSERT INTO exam_answer (id, paper_id, student_id, student_no, student_name, student_class, answer_source, import_batch_no, answer_content, paper_img_url, ocr_text, ai_reference_score, teacher_final_score, score_diff, score_modified, marking_status, marking_error, review_status, reviewer_id, reviewer_name, review_remark, review_time, submit_time, mark_time, create_time, update_time, del_flag, sim_hash) VALUES
(9017, 9003, 9001, '20230101', '演示学生', '计算机2023级2班', 'ONLINE', NULL,
 '[{"questionId":9009,"score":25},{"questionId":9010,"score":26},{"questionId":9011,"score":31}]',
 NULL, NULL, 78.00, 82.00, 4.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后上调分数', DATE_SUB(NOW(), INTERVAL 6 DAY),
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0, NULL),
(9018, 9003, 9002, '20230102', '李思远', '计算机2023级2班', 'ONLINE', NULL,
 '[{"questionId":9009,"score":20},{"questionId":9010,"score":22},{"questionId":9011,"score":28}]',
 NULL, NULL, 72.00, 70.00, -2.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 6 DAY),
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0, NULL),
(9019, 9003, 9009, '20230109', '周可欣', '软件工程2023级1班', 'ONLINE', NULL,
 '[{"questionId":9009,"score":20},{"questionId":9010,"score":20},{"questionId":9011,"score":25}]',
 NULL, NULL, 65.00, NULL, NULL, 0, 3, NULL, 0, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0, NULL);

-- ---------- 已归档试卷的答卷（试卷 9004，用于成绩导出） ----------
INSERT INTO exam_answer (id, paper_id, student_id, student_no, student_name, student_class, answer_source, import_batch_no, answer_content, paper_img_url, ocr_text, ai_reference_score, teacher_final_score, score_diff, score_modified, marking_status, marking_error, review_status, reviewer_id, reviewer_name, review_remark, review_time, submit_time, mark_time, create_time, update_time, del_flag, sim_hash) VALUES
(9020, 9004, 9010, '20230110', '吴俊杰', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261010-001',
 '[{"questionId":9012,"score":20},{"questionId":9013,"score":20}]',
 NULL, '堆排序先建大顶堆，再不断交换堆顶与末尾元素。直接插入排序每趟把当前元素插入到前面有序区。',
 43.00, 40.00, -3.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 20 DAY),
 DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0, NULL),
(9021, 9004, 9011, '20230111', '郑欣怡', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261010-001',
 '[{"questionId":9012,"score":22},{"questionId":9013,"score":23}]',
 NULL, '堆排序的核心是维护大顶堆，插入排序每次将一个元素插入有序序列。',
 40.00, 45.00, 5.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后上调分数', DATE_SUB(NOW(), INTERVAL 20 DAY),
 DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0, NULL),
(9022, 9004, 9012, '20230112', '冯宇航', '软件工程2023级1班', 'PAPER_SCAN', 'MOCK-20261010-001',
 '[{"questionId":9012,"score":15},{"questionId":9013,"score":17}]',
 NULL, '堆排序需要先建堆，插入排序是稳定的排序算法。',
 35.00, 32.00, -3.00, 1, 5, NULL, 2, 1, '系统管理员', '教师复核后下调分数', DATE_SUB(NOW(), INTERVAL 20 DAY),
 DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0, NULL);

-- ---------- 提示词模板 ----------
INSERT INTO prompt_template (id, template_code, template_name, question_type, course_id, system_prompt, user_prompt, model_name, temperature, version, status, remark, create_time, update_time, del_flag) VALUES
(9001, 'CALCULATION_DEFAULT', '计算题评分模板', 7, 1, '你是严谨的高校阅卷老师，请按步骤给分，只输出评分结果。', '题目：{question}\n标准答案：{standardAnswer}\n评分规则：{scoringRule}\n学生答案：{answer}', 'qwen-plus', 0.20, 1, 1, '适用于数据结构与高数计算题', NOW(), NOW(), 0),
(9002, 'CASE_DEFAULT', '案例题评分模板', 8, 1, '你是高校课程阅卷老师，请从分析深度与方案可行性两个维度评分。', '案例：{question}\n参考答案：{standardAnswer}\n评分规则：{scoringRule}\n学生作答：{answer}', 'qwen-plus', 0.30, 1, 1, '适用于案例分析题', NOW(), NOW(), 0),
(9003, 'GENERAL_FALLBACK', '通用兜底模板', 0, NULL, '你是高校阅卷老师，请依据评分规则客观评分。', '题目：{question}\n参考答案：{standardAnswer}\n学生答案：{answer}', 'qwen-turbo', 0.30, 1, 0, '题型未配置专用模板时使用', NOW(), NOW(), 0);

-- ---------- 学情分析报告（讲评建议 / PPT 页面演示） ----------
INSERT INTO learning_analysis_report (id, paper_id, course_id, report_type, report_title, analysis_text, ppt_file_url, status, error_msg, creator_id, create_time, update_time, del_flag) VALUES
(9001, 9001, 1, 'PAPER', '数据结构与算法 期末模拟卷 学情讲评',
 '# 学情分析\n\n## 一、整体情况\n本卷共 16 份纸质答卷，已完成阅卷 8 份，平均分 75.4 分，及格率 87.5%，优秀率 12.5%。\n\n## 二、得分分布\n- 90 分以上：1 人\n- 80-89 分：2 人\n- 70-79 分：2 人\n- 60-69 分：2 人\n- 60 分以下：1 人\n\n## 三、薄弱知识点\n1. 排序算法稳定性：多份答卷混淆了快速排序与归并排序的稳定性。\n2. 哈希冲突处理：开放定址法与链地址法的适用场景描述不完整。\n3. 综合案例：索引与缓存组合优化思路缺少落地细节。\n\n## 四、讲评建议\n- 用对比表格梳理常见排序算法的时间、空间与稳定性。\n- 增加哈希表冲突处理的手写练习。\n- 结合订单查询案例，演示执行计划与索引命中情况。',
 NULL, 1, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),
(9002, 9003, 2, 'PAPER', '高等数学（上）期中模拟 学情讲评',
 '# 学情分析\n\n## 一、整体情况\n本卷共 3 份答卷，平均分 76.0 分，及格率 100%。\n\n## 二、薄弱知识点\n- 定积分分部积分的符号处理仍有失分。\n- 拉格朗日中值定理的辅助函数构造步骤不完整。\n\n## 三、讲评建议\n- 增加分部积分典型题型的步骤拆解。\n- 用几何直观解释中值定理的结论。',
 NULL, 1, NULL, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0);

-- ============================================================
-- 清理演示数据（需要时取消注释执行）
-- ============================================================
-- DELETE FROM question_knowledge_rel WHERE question_id BETWEEN 9001 AND 9099 OR knowledge_id BETWEEN 9001 AND 9099;
-- DELETE FROM learning_analysis_report WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM exam_answer WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM exam_question WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM paper_assignment WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM class_student WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM class_info WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM exam_paper WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM question_knowledge WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM prompt_template WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM sys_user_role WHERE user_id BETWEEN 9001 AND 9099;
-- DELETE FROM sys_user WHERE id BETWEEN 9001 AND 9099;
-- DELETE FROM course WHERE id BETWEEN 9001 AND 9099;
