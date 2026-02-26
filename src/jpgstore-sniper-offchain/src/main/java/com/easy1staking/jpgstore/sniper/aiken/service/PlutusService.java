package com.easy1staking.jpgstore.sniper.aiken.service;

import com.easy1staking.jpgstore.sniper.aiken.model.Plutus;
import com.easy1staking.jpgstore.sniper.aiken.model.Validator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
public class PlutusService {

    private final Plutus plutus;

    public PlutusService(ObjectMapper objectMapper, Resource plutusJsonResource) {
        try (InputStream is = plutusJsonResource.getInputStream()) {
            this.plutus = objectMapper.readValue(is, Plutus.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load plutus.json from " + plutusJsonResource, e);
        }
    }

    public Optional<String> getContractCode(String contractTitle) {
        return plutus.validators().stream()
                .filter(validator -> validator.title().equals(contractTitle))
                .findAny()
                .map(Validator::compiledCode);
    }

}
