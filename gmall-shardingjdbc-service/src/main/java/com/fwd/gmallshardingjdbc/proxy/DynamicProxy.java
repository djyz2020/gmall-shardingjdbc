package com.fwd.gmallshardingjdbc.proxy;


import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@Slf4j
public class DynamicProxy implements InvocationHandler {
    private Object target;

    public DynamicProxy(Object target) {
        this.target = target;
    }

    public Object getProxy() {
        return Proxy.newProxyInstance(this.getClass().getClassLoader(), target.getClass().getInterfaces(), this);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] objects) throws Throwable {
        before();
        Object obj = method.invoke(target, objects);
        after();
        return obj;
    }

    private void after() {
        log.info("invoke service after method.");
    }

    private void before() {
        log.info("invoke service before method.");
    }

}
