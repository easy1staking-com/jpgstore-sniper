CREATE TABLE nft_token_trait (
    asset_id     VARCHAR(120) NOT NULL REFERENCES nft_token(asset_id),
    category     VARCHAR(255) NOT NULL,
    value        VARCHAR(255) NOT NULL,
    PRIMARY KEY (asset_id, category)
);

CREATE INDEX idx_nft_token_trait_category_value ON nft_token_trait(category, value);
