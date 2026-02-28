package com.easy1staking.jpgstore.sniper.aiken.service;

import com.easy1staking.jpgstore.sniper.aiken.model.Plutus;
import com.easy1staking.jpgstore.sniper.aiken.model.Validator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
@Slf4j
public class PlutusService {

    private final Plutus plutus;

    public PlutusService(ObjectMapper objectMapper, @Value("${plutus.json-path}") Resource plutusJsonResource) {
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
