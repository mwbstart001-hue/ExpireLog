DROP TABLE IF EXISTS member_expire_log;
DROP TABLE IF EXISTS user_member;
DROP TABLE IF EXISTS member_order;

CREATE TABLE user_member (
    user_id BIGSERIAL PRIMARY KEY,
    expire_time TIMESTAMP NOT NULL
);

CREATE TABLE member_order (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    duration_days INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE member_expire_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_days INT NOT NULL,
    order_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (order_id)
);

CREATE INDEX idx_member_expire_log_user_id ON member_expire_log(user_id);
CREATE INDEX idx_member_order_user_id ON member_order(user_id);
