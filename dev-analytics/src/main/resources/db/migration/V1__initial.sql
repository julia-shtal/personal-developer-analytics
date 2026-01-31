-- V1__initial.sql

CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       username      VARCHAR(255) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       timezone      VARCHAR(64)  NOT NULL DEFAULT 'Europe/Berlin'
);

CREATE TABLE data_source_configs (
                                     id                 BIGSERIAL PRIMARY KEY,
                                     user_id            BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                     type               VARCHAR(50) NOT NULL,          -- EnumType.STRING
                                     name               VARCHAR(255) NOT NULL,
                                     base_url           VARCHAR(512),
                                     path               VARCHAR(1024),
                                     api_token          VARCHAR(1024),
                                     last_success_sync  TIMESTAMP
);
