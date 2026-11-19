package com.tone.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final boolean sandboxActive;

    public PublicConfigController(
            Environment environment,
            @Value("${app.payment.sandbox-enabled:false}") boolean sandboxFlag
    ) {
        this.sandboxActive = sandboxFlag && environment.acceptsProfiles(Profiles.of("dev | test"));
    }

    @GetMapping("/public")
    public Map<String, Object> publicConfig() {
        return Map.of("sandboxEnabled", sandboxActive);
    }
}
