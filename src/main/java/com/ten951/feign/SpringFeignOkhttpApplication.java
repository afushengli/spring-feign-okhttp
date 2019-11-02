package com.ten951.feign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class SpringFeignOkhttpApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringFeignOkhttpApplication.class, args);
	}

}
