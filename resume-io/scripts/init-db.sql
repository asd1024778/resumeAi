-- 手动初始化 resume_db（可选；Spring Boot 启动时也会通过 JPA 自动建表）
CREATE DATABASE IF NOT EXISTS resume_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE resume_db;
