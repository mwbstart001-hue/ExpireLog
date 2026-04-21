DROP TABLE IF EXISTS member_reminder;
DROP TABLE IF EXISTS user_reminder_setting;
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

CREATE TABLE user_reminder_setting (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    channels VARCHAR(100) NOT NULL DEFAULT 'SMS,EMAIL',
    days_before_expire INT DEFAULT 7,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_reminder_setting_user_id ON user_reminder_setting(user_id);

CREATE TABLE member_reminder (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    remind_time TIMESTAMP NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, remind_time, channel)
);

CREATE INDEX idx_member_reminder_user_id ON member_reminder(user_id);
CREATE INDEX idx_member_reminder_status ON member_reminder(status);
CREATE INDEX idx_member_reminder_created_at ON member_reminder(created_at);
