package com.easy1staking.jpgstore.sniper.controller;

import com.easy1staking.jpgstore.sniper.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${apiPrefix}/settings")
@Slf4j
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<SettingsService.SettingsDto> getSettings() {
        return settingsService.getSettings()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
