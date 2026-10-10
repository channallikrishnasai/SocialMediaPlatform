CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255),
    bio             VARCHAR(500),
    avatar_url      VARCHAR(500),
    oauth_provider  VARCHAR(50),
    oauth_id        VARCHAR(255),
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);
CREATE INDEX idx_email ON users (email);
CREATE INDEX idx_username ON users (username);

CREATE TABLE posts (
    id              BIGSERIAL PRIMARY KEY,
    content         TEXT NOT NULL,
    image_url       VARCHAR(500),
    share_count     BIGINT DEFAULT 0,
    user_id         BIGINT NOT NULL REFERENCES users (id),
    shared_post_id  BIGINT REFERENCES posts (id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);
CREATE INDEX idx_user_created ON posts (user_id, created_at);
CREATE INDEX idx_created_at ON posts (created_at);

CREATE TABLE comments (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT NOT NULL,
    post_id     BIGINT NOT NULL REFERENCES posts (id),
    user_id     BIGINT NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP
);
CREATE INDEX idx_post_created ON comments (post_id, created_at);

CREATE TABLE likes (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT NOT NULL REFERENCES posts (id),
    user_id     BIGINT NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_likes_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX idx_post_id ON likes (post_id);
CREATE INDEX idx_user_id ON likes (user_id);

CREATE TABLE follows (
    id            BIGSERIAL PRIMARY KEY,
    follower_id   BIGINT NOT NULL REFERENCES users (id),
    following_id  BIGINT NOT NULL REFERENCES users (id),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_follows_follower_following UNIQUE (follower_id, following_id)
);
CREATE INDEX idx_follower_id ON follows (follower_id);
CREATE INDEX idx_following_id ON follows (following_id);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (id),
    type            VARCHAR(50) NOT NULL,
    message         TEXT NOT NULL,
    related_user_id BIGINT,
    related_post_id BIGINT,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notif_user_read ON notifications (user_id, is_read);
