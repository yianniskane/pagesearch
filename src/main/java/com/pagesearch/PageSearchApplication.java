package com.pagesearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PageSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(PageSearchApplication.class, args);
    }
}
