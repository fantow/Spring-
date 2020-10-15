package com.fantow.mvcframework.Service;

import com.fantow.mvcframework.annotation.MyService;

@MyService
public class ServiceTest1 implements ServicTest1Interface{

    public void method1(){
        System.out.println("Service1被调用...");
    }

}
