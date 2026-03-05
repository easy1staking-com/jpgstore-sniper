package com.easy1staking.jpgstore.sniper.controller;

import com.easy1staking.jpgstore.sniper.model.Snipe;
import com.easy1staking.jpgstore.sniper.service.SnipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${apiPrefix}/snipes")
@Slf4j
@RequiredArgsConstructor
public class SnipeController {

    private final SnipeService snipeService;

    @GetMapping
    public ResponseEntity<List<Snipe>> getAllSnipes() {
        return ResponseEntity.ok(snipeService.findAllSnipes());
    }

}
