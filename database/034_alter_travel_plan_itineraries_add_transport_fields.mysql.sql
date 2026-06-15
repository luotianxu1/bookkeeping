USE bookkeeping_app;

SET NAMES utf8mb4;

ALTER TABLE travel_plan_itineraries
  ADD COLUMN transport_mode ENUM('driving', 'walking', 'riding') NULL COMMENT '交通方式：驾车、步行、骑车' AFTER start_time,
  ADD COLUMN distance_meters INT NULL COMMENT '路程距离，单位米' AFTER transport_mode,
  ADD COLUMN duration_seconds INT NULL COMMENT '预计时长，单位秒' AFTER distance_meters;
