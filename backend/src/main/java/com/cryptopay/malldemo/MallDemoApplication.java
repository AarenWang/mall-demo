package com.cryptopay.malldemo;

import com.cryptopay.malldemo.config.MallDemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MallDemoProperties.class)
public class MallDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallDemoApplication.class, args);
    }
}
