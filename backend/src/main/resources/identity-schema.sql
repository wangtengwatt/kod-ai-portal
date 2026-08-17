CREATE TABLE IF NOT EXISTS sys_user_identity (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    issuer          VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    email           VARCHAR(128) NOT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_identity_issuer_subject (issuer, subject),
    UNIQUE KEY uk_user_identity_user_issuer (user_id, issuer),
    KEY idx_user_identity_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'KOD external identity account links';
