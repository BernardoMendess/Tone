package com.toneguitar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ToneGuitarApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToneGuitarApplication.class, args);
    }
}
