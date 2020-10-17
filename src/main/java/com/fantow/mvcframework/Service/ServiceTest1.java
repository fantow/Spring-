package com.fantow.mvcframework.Service;

import com.fantow.mvcframework.annotation.MyService;
import org.springframework.stereotype.Service;

@MyService
@Service
public class ServiceTest1 implements ServicTest1Interface{

    public void method1(){
        System.out.println("Service1被调用...");
    }

}
