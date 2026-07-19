package com.simulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SimulationServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulationServerApplication.class, args);
    }
}
