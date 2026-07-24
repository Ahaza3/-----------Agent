-- V14__grid_topology_mvp.sql — 拓扑化负荷监控 MVP

CREATE TABLE grid_node (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    node_code         VARCHAR(64) NOT NULL COMMENT '拓扑节点编码',
    node_name         VARCHAR(100) NOT NULL COMMENT '拓扑节点名称',
    node_type         VARCHAR(32) NOT NULL COMMENT '节点类型: REGION/SUBSTATION/FEEDER',
    parent_id         BIGINT NULL COMMENT '父节点 ID',
    allocation_ratio  DECIMAL(8,5) NOT NULL DEFAULT 1.00000 COMMENT '相对父节点的模拟负荷分配比例',
    rated_capacity_mw FLOAT NULL COMMENT '额定容量(MW)',
    voltage_level     VARCHAR(32) NULL COMMENT '电压等级',
    status            VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE' COMMENT '运行状态',
    topology_version  VARCHAR(32) NOT NULL DEFAULT 'demo-v1' COMMENT '拓扑版本',
    sort_order        INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX uk_grid_node_code (node_code),
    INDEX idx_grid_node_parent (parent_id),
    INDEX idx_grid_node_type (node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电网拓扑节点';

CREATE TABLE grid_edge (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_node_id     BIGINT NOT NULL COMMENT '起点节点 ID',
    to_node_id       BIGINT NOT NULL COMMENT '终点节点 ID',
    edge_type        VARCHAR(32) NOT NULL DEFAULT 'HIERARCHY' COMMENT '边类型',
    capacity_mw      FLOAT NULL COMMENT '边容量(MW)',
    status           VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE' COMMENT '运行状态',
    topology_version VARCHAR(32) NOT NULL DEFAULT 'demo-v1' COMMENT '拓扑版本',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE INDEX uk_grid_edge (from_node_id, to_node_id, topology_version),
    INDEX idx_grid_edge_from (from_node_id),
    INDEX idx_grid_edge_to (to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电网拓扑连接关系';

ALTER TABLE load_data
    DROP INDEX idx_time,
    ADD COLUMN node_id BIGINT NULL COMMENT '归属拓扑节点 ID' AFTER id,
    ADD INDEX idx_load_node_time (node_id, time);

ALTER TABLE prediction_result
    ADD COLUMN node_id BIGINT NULL COMMENT '预测目标拓扑节点 ID' AFTER id,
    ADD INDEX idx_prediction_node_time (node_id, predict_time);

ALTER TABLE alert_event
    ADD COLUMN node_id BIGINT NULL COMMENT '告警对象拓扑节点 ID' AFTER id,
    ADD COLUMN root_event_id BIGINT NULL COMMENT '关联的根告警事件 ID',
    ADD COLUMN impact_load_mw FLOAT NULL COMMENT '预计影响负荷(MW)',
    ADD INDEX idx_alert_node_time (node_id, trigger_time),
    ADD INDEX idx_alert_root_event (root_event_id);

INSERT INTO grid_node
    (id, node_code, node_name, node_type, parent_id, allocation_ratio, rated_capacity_mw, voltage_level, sort_order)
VALUES
    (1, 'REGION-DEMO', '演示区域', 'REGION', NULL, 1.00000, 1600.0, '220kV', 1),
    (2, 'SUBSTATION-EAST', '东部变电站', 'SUBSTATION', 1, 0.55000, 900.0, '110kV', 1),
    (3, 'SUBSTATION-WEST', '西部变电站', 'SUBSTATION', 1, 0.45000, 700.0, '110kV', 2),
    (4, 'FEEDER-E-01', '东部一号馈线', 'FEEDER', 2, 0.50000, 450.0, '35kV', 1),
    (5, 'FEEDER-E-02', '东部二号馈线', 'FEEDER', 2, 0.50000, 450.0, '35kV', 2),
    (6, 'FEEDER-W-01', '西部一号馈线', 'FEEDER', 3, 0.60000, 420.0, '35kV', 1),
    (7, 'FEEDER-W-02', '西部二号馈线', 'FEEDER', 3, 0.40000, 280.0, '35kV', 2);

INSERT INTO grid_edge
    (from_node_id, to_node_id, edge_type, capacity_mw)
VALUES
    (1, 2, 'HIERARCHY', 900.0),
    (1, 3, 'HIERARCHY', 700.0),
    (2, 4, 'HIERARCHY', 450.0),
    (2, 5, 'HIERARCHY', 450.0),
    (3, 6, 'HIERARCHY', 420.0),
    (3, 7, 'HIERARCHY', 280.0);

UPDATE load_data SET node_id = 1 WHERE node_id IS NULL;
UPDATE prediction_result SET node_id = 1 WHERE node_id IS NULL;
UPDATE alert_event SET node_id = 1 WHERE node_id IS NULL;

CREATE UNIQUE INDEX uk_load_node_time ON load_data (node_id, time);
