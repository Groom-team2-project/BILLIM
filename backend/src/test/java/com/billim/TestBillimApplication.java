package com.billim;

import org.springframework.boot.SpringApplication;

public class TestBillimApplication {

    public static void main(String[] args) {
        SpringApplication.from(BillimApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
