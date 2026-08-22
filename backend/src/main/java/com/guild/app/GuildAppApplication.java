package com.guild.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GuildAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuildAppApplication.class, args);
    }
}
