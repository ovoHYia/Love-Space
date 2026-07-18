package com.lovespace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoveSpaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoveSpaceApplication.class, args);
    }
}
