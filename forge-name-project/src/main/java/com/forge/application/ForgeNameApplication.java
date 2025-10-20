package com.forge.application;

import com.forge.infrastructure.properties.ModerationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.forge")
@EnableConfigurationProperties(ModerationProperties.class)
public class ForgeNameApplication {

	public static void main(String[] args) {
		SpringApplication.run(ForgeNameApplication.class, args);
	}

}
