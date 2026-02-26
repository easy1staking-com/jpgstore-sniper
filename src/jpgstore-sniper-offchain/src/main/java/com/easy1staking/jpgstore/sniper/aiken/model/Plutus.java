package com.easy1staking.jpgstore.sniper.aiken.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Plutus(List<Validator> validators) {
}
