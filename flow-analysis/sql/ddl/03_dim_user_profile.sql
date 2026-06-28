-- 03_dim_user_profile.sql: 用户画像维度表
CREATE TABLE IF NOT EXISTS flow.dim_user_profile
(
    imsi           String,
    gender         Enum8('男' = 1, '女' = 2),
    age            UInt8,
    age_group      String,
    is_resident    UInt8,
    home_region_id String
)
ENGINE = ReplacingMergeTree
ORDER BY imsi;
