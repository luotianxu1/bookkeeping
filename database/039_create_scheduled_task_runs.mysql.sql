-- Scheduled task execution audit table.
-- Usage:
--   mysql -u root -p bookkeeping_app < database/039_create_scheduled_task_runs.mysql.sql

CREATE TABLE IF NOT EXISTS scheduled_task_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务运行ID',
  task_name VARCHAR(120) NOT NULL COMMENT '任务名称',
  trigger_name VARCHAR(120) NOT NULL COMMENT '触发来源',
  status ENUM('running', 'success', 'failed') NOT NULL DEFAULT 'running' COMMENT '运行状态',
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
  finished_at DATETIME(3) NULL COMMENT '结束时间',
  duration_millis BIGINT UNSIGNED NULL COMMENT '运行耗时毫秒',
  result_message VARCHAR(1000) NULL COMMENT '运行结果摘要',
  error_message VARCHAR(1000) NULL COMMENT '失败原因',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_scheduled_task_runs_task_started (task_name, started_at),
  KEY idx_scheduled_task_runs_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务运行审计表';
