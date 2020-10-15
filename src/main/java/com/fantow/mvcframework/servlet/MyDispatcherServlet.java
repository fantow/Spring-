package com.fantow.mvcframework.servlet;

import com.fantow.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    // 用于保存扫描到的类名
    private List<String> classNames = new ArrayList<>();

    // 一个IOC容器
    private Map<String,Object> iocMap = new HashMap<>();

    // 用于存放HandlerMap
    private Map<String,Method> handlerMap = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Server Exception,Detail: " + Arrays.toString(e.getStackTrace()));
        }

    }

    private void doDispatcher(HttpServletRequest req,HttpServletResponse resp) throws Exception{

        String uri = req.getRequestURI();
        if(handlerMap.containsKey(uri)){
            Method method = handlerMap.get(uri);
            String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
            Object obj = null;
            if(iocMap.containsKey(beanName)){
                obj = iocMap.get(beanName);
            }

            if(obj != null){
                Map<String, String[]> parameterMap = req.getParameterMap();
                // 获取被调用方法的形参列表
//                Class<?>[] parameterTypes = method.getParameterTypes();
                Parameter[] parameters = method.getParameters();
                Object[] objs = new Object[parameters.length];

                for(int i = 0;i < parameters.length;i++){
                    Parameter parameter = parameters[i];
                    if(parameter.getType() == HttpServletRequest.class){
                        objs[i] = req;
                        continue;
                    }else if(parameter.getType() == HttpServletResponse.class){
                        objs[i] = resp;
                        continue;
                    }else if(parameter.getType() == String.class){
                        // 这个是用来接收Json格式的
//                        MyRequestParam requestParam = (MyRequestParam)type.getAnnotation(MyRequestParam.class);
                        MyRequestParam requestParam = parameter.getAnnotation(MyRequestParam.class);
                        if(parameterMap.containsKey(requestParam.value())){
                            objs[i] = parameterMap.get(requestParam.value())[0];
                        }
                    }

                }

                method.invoke(obj, objs);
            }else{
                // 应该不会发生
                throw new Exception("Object not Find");
            }
        }else{
            resp.getWriter().write("404 Page not find.");
            return;
        }


    }


    @Override
    public  void init(ServletConfig config) throws ServletException {

//        1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

//        2.扫描相关的类
        doScanner(properties.getProperty("scanPackage"));

//        3.初始化扫描到的类，并将它们放入IOC容器中
        doInstance();

//        System.out.println("容器当前情况：");
//        for(Map.Entry entry : iocMap.entrySet()){
//            System.out.println("key:" + entry.getKey() + " : " + "value:" + entry.getValue());
//        }


//        4.完成依赖注入
        doAutowired();

//        5.初始化HandlerMapping
        initHandlerMapping();
    }

    // 这里的HandlerMapping是指，比如在类上写的@Mapping("XXXX")，以及在方法上写的@Mapping("XXXX")
    private void initHandlerMapping() {
        if(iocMap.isEmpty()){
            return;
        }

        for(Map.Entry<String,Object> entry : iocMap.entrySet()){
            // 测过了，即使对象是以Object类型存的，还是可以通过getClass()获取到它真正的类型
            Class<?> clazz = entry.getValue().getClass();

            // 有Mapping的前提是，它是一个Controller并且
            if(!clazz.isAnnotationPresent(MyController.class)){
                continue;
            }

            String baseUrl = "";

            // 在类上的@MyMapping，可能有也可能没有
            if(clazz.isAnnotationPresent(MyMapping.class)){
                baseUrl += clazz.getAnnotation(MyMapping.class).value();
            }

            // 接着遍历这个类中的所有方法，找到每个方法的url
            Method[] methods = clazz.getDeclaredMethods();
            for(Method method : methods){
                if(method.isAnnotationPresent(MyMapping.class)){
                    String methodUrl = method.getAnnotation(MyMapping.class).value();
                    handlerMap.put(baseUrl + methodUrl,method);
                }
            }
        }
    }

    // 这一步是将一个对象中，被@Autowire修饰的属性，赋值
    private void doAutowired() {
        if(iocMap.isEmpty()){
            return;
        }

        for(Map.Entry<String,Object> entry : iocMap.entrySet()){
            // 获取到这个对象的所有的属性，再判断哪些属性被注解修饰
            Object clazz = entry.getValue();
            Field[] fields = clazz.getClass().getDeclaredFields();
            for(Field field : fields){
                // new出来的对象，其中的成员变量还需要被初始化，否则为null
                if(field.isAnnotationPresent(MyAutowire.class)){
                    MyAutowire myAutowire = field.getAnnotation(MyAutowire.class);
                    String beanName = myAutowire.value();

                    if(beanName.length() == 0){
                        // 为默认值
                        beanName = toLowerFirstCase(field.getType().getSimpleName());
                    }

                    field.setAccessible(true);

                    try {
                        // 对field进行初始化
                        field.set(entry.getValue(), iocMap.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }else{
                    continue;
                }
            }
        }
    }


    // 可以发现，这个构造方式都是单例模式
    // 执行完这步后，iocMap容器中全是new出来的对象，还没有被赋值
    private void doInstance() {
        if(classNames.isEmpty()){
            return ;
        }

        try{
            for(String className : classNames){
                Class<?> clazz = Class.forName(className);

                // 将带注解的类进行初始化
                if(clazz.isAnnotationPresent(MyController.class) || clazz.isAnnotationPresent(MyService.class)){
                    // 如果没有为@MyController(),@MyService起名字，默认使用类名首字母小写作为beanName
                    if(clazz.getAnnotation(MyController.class) == null || clazz.getAnnotation(MyService.class) == null){
                        // 对这样的类，进行初始化
                        // 这样有一个问题，比如@Autowire这样没有写在类上的注解，怎么扫描到？
                        Object instance = clazz.newInstance();
                        // 现在这个类型是Object，多时可以将其转化为正确的类型呢？
                        String beanName = toLowerFirstCase(clazz.getSimpleName());
                        iocMap.put(beanName,instance);
                    }else{
                        // 如果起名了，使用指定名字作为beanName
                        String beanName = toLowerFirstCase(clazz.getSimpleName());
                        if(clazz.isAnnotationPresent(MyController.class)){
                            // 如果是Controller
                            MyController controller = clazz.getAnnotation(MyController.class);
                            String controllerName = controller.value();
                            beanName = toLowerFirstCase(controllerName);
                        }else if(clazz.isAnnotationPresent(MyService.class)) {
                            // 如果是Service
                            MyService myService = clazz.getAnnotation(MyService.class);
                            String serviceName = myService.value();
                            beanName = toLowerFirstCase(serviceName);
                        }
                        Object newInstance = clazz.newInstance();
                        iocMap.put(beanName,newInstance);
                    }
                }else{
                    continue;
                }
            }

        }catch (ClassNotFoundException ex){
            ex.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }


    }

    // 首字母转小写
    private String toLowerFirstCase(String simpleName) {
        char[] charArray = simpleName.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

    private void doScanner(String scanPackage) {
        // 将包路径替换为文件路径
        URL resource = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(resource.getFile());

        // 尝试遍历文件
        for(File file : classPath.listFiles()){
            if(file.isDirectory()){
                // 如果是文件夹，递归
                doScanner(scanPackage + "." + file.getName());
            }else{
                // 找到我们需要的class文件
                if(file.getName().endsWith(".class")){
                    //根据文件名获取到完整的类名
                    String className = scanPackage + "." + file.getName().replace(".class","");
                    classNames.add(className);
                }else{
                    continue;
                }
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation){

        InputStream fis = null;
        fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(fis);

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}



