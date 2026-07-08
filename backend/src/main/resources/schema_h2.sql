-- H2 兼容版 schema (MySQL 兼容模式)
CREATE TABLE IF NOT EXISTS task (
    task_id      BIGINT       NOT NULL AUTO_INCREMENT,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    PRIMARY KEY (task_id)
);

CREATE TABLE IF NOT EXISTS location (
    loc_id   BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(128),
    lng      DOUBLE       NOT NULL,
    lat      DOUBLE       NOT NULL,
    PRIMARY KEY (loc_id)
);
CREATE INDEX IF NOT EXISTS idx_lng_lat ON location(lng, lat);

INSERT INTO location (loc_id, name, lng, lat)
SELECT 0, '默认地点(武汉)', 114.305469, 30.592849
WHERE NOT EXISTS (SELECT 1 FROM location WHERE loc_id = 0);
ALTER TABLE location ALTER COLUMN loc_id RESTART WITH 1;

CREATE TABLE IF NOT EXISTS distance (
    from_loc_id  BIGINT   NOT NULL,
    to_loc_id    BIGINT   NOT NULL,
    travel_time  INT      NOT NULL,
    dist         DOUBLE   NOT NULL,
    source       VARCHAR(16) NOT NULL DEFAULT 'euclid',
    PRIMARY KEY (from_loc_id, to_loc_id)
);
CREATE INDEX IF NOT EXISTS idx_from ON distance(from_loc_id);

CREATE TABLE IF NOT EXISTS order_table (
    order_id        BIGINT       NOT NULL AUTO_INCREMENT,
    task_id         BIGINT       NOT NULL,
    pickup_loc_id   BIGINT       NOT NULL,
    delivery_loc_id BIGINT       NOT NULL,
    time_start      TIMESTAMP    NOT NULL,
    time_end        TIMESTAMP    NOT NULL,
    revenue         DECIMAL(10,2) NOT NULL DEFAULT 0,
    weight          DECIMAL(10,2) NOT NULL DEFAULT 0,
    volume          DECIMAL(10,2) NOT NULL DEFAULT 0,
    service_time    INT          NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'UNASSIGNED',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    FOREIGN KEY (task_id) REFERENCES task(task_id),
    FOREIGN KEY (pickup_loc_id) REFERENCES location(loc_id),
    FOREIGN KEY (delivery_loc_id) REFERENCES location(loc_id)
);
CREATE INDEX IF NOT EXISTS idx_task ON order_table(task_id);
CREATE INDEX IF NOT EXISTS idx_status ON order_table(status);
CREATE INDEX IF NOT EXISTS idx_time ON order_table(time_start);

CREATE TABLE IF NOT EXISTS vehicle (
    vehicle_id          BIGINT       NOT NULL AUTO_INCREMENT,
    person_id           VARCHAR(64),
    status              VARCHAR(32)  NOT NULL DEFAULT 'IDLE',
    cur_loc_id          BIGINT       NOT NULL,
    cur_available_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tool_type           VARCHAR(64),
    max_weight          DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_volume          DECIMAL(10,2) NOT NULL DEFAULT 0,
    speed               DECIMAL(6,2)  NOT NULL DEFAULT 30.0,
    shift_start         TIMESTAMP,
    shift_end           TIMESTAMP,
    earned_revenue      DECIMAL(12,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (vehicle_id),
    FOREIGN KEY (cur_loc_id) REFERENCES location(loc_id)
);
CREATE INDEX IF NOT EXISTS idx_v_status ON vehicle(status);

CREATE TABLE IF NOT EXISTS route (
    route_id        BIGINT       NOT NULL AUTO_INCREMENT,
    vehicle_id      BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PLANNED',
    total_idle      INT          NOT NULL DEFAULT 0,
    total_revenue   DECIMAL(12,2) NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (route_id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)
);
CREATE INDEX IF NOT EXISTS idx_r_vehicle ON route(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_r_status ON route(status);

CREATE TABLE IF NOT EXISTS route_item (
    route_id        BIGINT   NOT NULL,
    seq             INT      NOT NULL,
    order_id        BIGINT   NOT NULL,
    planned_start   TIMESTAMP NOT NULL,
    planned_end     TIMESTAMP NOT NULL,
    idle_before     INT      NOT NULL DEFAULT 0,
    load_weight     DECIMAL(10,2) NOT NULL DEFAULT 0,
    load_volume     DECIMAL(10,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (route_id, seq),
    FOREIGN KEY (route_id) REFERENCES route(route_id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES order_table(order_id)
);
CREATE INDEX IF NOT EXISTS idx_ri_order ON route_item(order_id);

CREATE TABLE IF NOT EXISTS unassigned_order (
    order_id    BIGINT      NOT NULL,
    reason      VARCHAR(256) NOT NULL,
    snapshot_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    FOREIGN KEY (order_id) REFERENCES order_table(order_id) ON DELETE CASCADE
);
