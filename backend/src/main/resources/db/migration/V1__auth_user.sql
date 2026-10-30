-- AUTH / USER만 생성하며 기존 데이터를 삭제하거나 PLAN 테이블을 변경하지 않는다.
CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash varchar(100) NOT NULL,
    nickname varchar(20) NOT NULL CHECK (char_length(nickname) BETWEEN 2 AND 20),
    timezone varchar(64),
    created_at timestamptz NOT NULL
);

CREATE TABLE workout_profile (
    user_id uuid PRIMARY KEY REFERENCES app_user(id),
    goal varchar(32) NOT NULL CHECK (goal IN ('FITNESS', 'STRENGTH', 'RUNNING', 'HABIT')),
    experience_level varchar(32) NOT NULL CHECK (experience_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    preferred_types text[] NOT NULL,
    equipment text[] NOT NULL,
    -- 허용값별 출현 여부의 합과 길이가 같아야 하므로 중복 원소도 차단한다.
    CONSTRAINT valid_types CHECK (
        array_ndims(preferred_types) = 1 AND cardinality(preferred_types) >= 1
        AND array_position(preferred_types, NULL) IS NULL
        AND preferred_types <@ ARRAY['RUNNING', 'CALISTHENICS']::text[]
        AND cardinality(preferred_types) =
            ('RUNNING' = ANY(preferred_types))::int + ('CALISTHENICS' = ANY(preferred_types))::int
    ),
    CONSTRAINT valid_equipment CHECK (
        array_ndims(equipment) = 1 AND cardinality(equipment) >= 1
        AND array_position(equipment, NULL) IS NULL
        AND equipment <@ ARRAY['NONE', 'MAT', 'PULL_UP_BAR', 'DUMBBELL']::text[]
        AND cardinality(equipment) = ('NONE' = ANY(equipment))::int + ('MAT' = ANY(equipment))::int
            + ('PULL_UP_BAR' = ANY(equipment))::int + ('DUMBBELL' = ANY(equipment))::int
        AND (NOT ('NONE' = ANY(equipment)) OR cardinality(equipment) = 1)
    )
);

-- 원문 JWT 대신 jti만 저장하여 재시작 후에도 로그아웃을 유지한다.
CREATE TABLE revoked_token (
    jti uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user(id),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NOT NULL
);
CREATE INDEX revoked_token_expiry_idx ON revoked_token(expires_at);
