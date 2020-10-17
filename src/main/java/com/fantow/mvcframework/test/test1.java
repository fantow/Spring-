package com.fantow.mvcframework.test;


import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URL;
import java.util.Map;
import java.util.Properties;

public class test1 {
    public static void main(String[] args) {
        ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");
        applicationContext.addBeanFactoryPostProcessor(null);

        System.out.println("1231231");
    }
}