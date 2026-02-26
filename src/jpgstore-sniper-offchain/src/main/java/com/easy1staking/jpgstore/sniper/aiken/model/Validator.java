package com.easy1staking.jpgstore.sniper.aiken.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Validator(String title, String compiledCode) {
}
