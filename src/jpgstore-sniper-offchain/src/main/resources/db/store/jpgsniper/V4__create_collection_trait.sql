CREATE TABLE collection_trait (
    id           SERIAL       PRIMARY KEY,
    policy_id    VARCHAR(56)  NOT NULL REFERENCES nft_collection(policy_id),
    category     VARCHAR(255) NOT NULL,
    value        VARCHAR(255) NOT NULL,
    nft_count    INT          NOT NULL DEFAULT 0,
    UNIQUE(policy_id, category, value)
);

CREATE INDEX idx_collection_trait_policy ON collection_trait(policy_id);
