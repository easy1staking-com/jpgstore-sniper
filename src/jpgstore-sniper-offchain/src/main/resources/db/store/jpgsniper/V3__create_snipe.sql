CREATE TABLE merkle_snipe (
    tx_hash      VARCHAR(64)  NOT NULL,
    output_index INT          NOT NULL,
    slot         BIGINT       NOT NULL,
    nft_list     TEXT         NOT NULL,
    PRIMARY KEY (tx_hash, output_index)
);
