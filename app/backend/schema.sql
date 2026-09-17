PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('ADMIN','MODERATOR','USER')),
    status        TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','BANNED')),
    avatar_url    TEXT,
    xp            INTEGER NOT NULL DEFAULT 0,
    fcm_token     TEXT,
    created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cards (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                TEXT NOT NULL,
    description         TEXT,
    latitude            REAL NOT NULL,
    longitude           REAL NOT NULL,
    radius_meters       INTEGER NOT NULL DEFAULT 50,
    reference_image_url TEXT,
    rarity              TEXT NOT NULL DEFAULT 'COMMON'
                        CHECK (rarity IN ('COMMON','RARE','EPIC','LEGENDARY')),
    points              INTEGER NOT NULL DEFAULT 10,
    is_active           INTEGER NOT NULL DEFAULT 1,
    created_by          INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at          TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cards_geo    ON cards (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_cards_active ON cards (is_active);

CREATE TABLE IF NOT EXISTS user_cards (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id              INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id              INTEGER NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    acquisition          TEXT NOT NULL CHECK (acquisition IN ('PHOTO','SHARE')),
    has_original_shoot   INTEGER NOT NULL DEFAULT 0,
    photo_url            TEXT,
    cloudinary_public_id TEXT,
    shared_from_user_id  INTEGER REFERENCES users(id) ON DELETE SET NULL,
    collected_at         TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, card_id)
);
CREATE INDEX IF NOT EXISTS idx_uc_user ON user_cards (user_id);

CREATE TABLE IF NOT EXISTS friendships (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    requester_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       TEXT NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
    created_at   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (requester_id, addressee_id)
);
CREATE INDEX IF NOT EXISTS idx_fs_addressee ON friendships (addressee_id, status);

CREATE TABLE IF NOT EXISTS card_shares (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_id   INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    card_id     INTEGER NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    share_token TEXT NOT NULL UNIQUE,
    is_redeemed INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TEXT NOT NULL DEFAULT (datetime('now', '+7 days')),
    redeemed_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_sh_receiver ON card_shares (receiver_id, is_redeemed);

CREATE TABLE IF NOT EXISTS achievements (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    description TEXT NOT NULL,
    icon        TEXT,
    threshold   INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS user_achievements (
    user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id INTEGER NOT NULL REFERENCES achievements(id) ON DELETE CASCADE,
    unlocked_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       TEXT NOT NULL CHECK (type IN ('SHARE_RECEIVED','FRIEND_REQUEST',
                 'FRIEND_ACCEPTED','CARD_NEARBY','ACHIEVEMENT','CARD_REMOVED','CARD_REDEEMED')),
    message    TEXT NOT NULL,
    payload    TEXT,
    is_read    INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_nt_user_read ON notifications (user_id, is_read);

INSERT OR IGNORE INTO achievements (code, name, description, icon, threshold) VALUES
('FIRST_CARD',     'First Blood',        'Collect your very first card',         'ic_first',   1),
('PHOTOGRAPHER',   'Original Shoot',     'Capture a card with your own photo',   'ic_camera',  1),
('COLLECTOR_1',    'Card Enthusiast',    'Collect 1 different card',             'ic_cards',   1),
('COLLECTOR_5',    'Card Hunter',        'Collect 5 different cards',            'ic_cards',   5),
('SOCIAL_CARDS',   'Sharing is Caring',  'Receive 10 shared cards',              'ic_share',   10),
('SOCIAL_FRIENDS', 'Team Builder',       'Have 10 friends',                      'ic_friends', 10);