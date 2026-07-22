-- 为变电站绑定责任运维人员，子节点告警沿父链归属到该责任域。

ALTER TABLE grid_node
    ADD COLUMN responsible_user_id BIGINT NULL COMMENT '责任运维人员 ID' AFTER parent_id,
    ADD INDEX idx_grid_node_responsible_user (responsible_user_id);
