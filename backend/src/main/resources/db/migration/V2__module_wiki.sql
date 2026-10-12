-- RC8 first-party wiki compatibility adapter. No existing tables are dropped.
CREATE TABLE sibyl_wiki_pages (
    id CHAR(36) NOT NULL PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    body LONGTEXT NOT NULL,
    owner VARCHAR(80) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_sibyl_wiki_updated (updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
