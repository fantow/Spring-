package com.fantow.mvcframework.Controller;

import com.fantow.mvcframework.Service.ServiceTest1;
import com.fantow.mvcframework.annotation.MyAutowire;
import com.fantow.mvcframework.annotation.MyController;
import com.fantow.mvcframework.annotation.MyMapping;
import com.fantow.mvcframework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyMapping("/test1")
public class ControllerTest {

    @MyAutowire
    private ServiceTest1 serviceTest1;

    @MyMapping("/method1")
    public void method1(HttpServletRequest request, HttpServletResponse response,@MyRequestParam("name") String name){
        System.out.println(serviceTest1);
        serviceTest1.method1();
        try {
            response.getWriter().write("Hello! " + name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
