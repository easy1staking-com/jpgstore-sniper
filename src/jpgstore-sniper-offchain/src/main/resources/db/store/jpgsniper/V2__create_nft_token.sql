CREATE TABLE nft_token (
    asset_id         VARCHAR(120) PRIMARY KEY,
    policy_id        VARCHAR(56)  NOT NULL REFERENCES nft_collection(policy_id),
    display_name     VARCHAR(255) NOT NULL,
    image            TEXT,
    optimized_source TEXT,
    mediatype        VARCHAR(100)
);
