package com.scalecart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ScaleCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScaleCartApplication.class, args);
    }
}
