package com.fwd.gmallshardingjdbc.controller;

import com.fwd.gmallshardingjdbc.GetUserInfoService;
import com.fwd.gmallshardingjdbc.proxy.DynamicProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
public class GreetingController {

	@Autowired
	private GetUserInfoService getUserInfoService;

	private GetUserInfoService proxyUserInfoService;

	@GetMapping("/greeting")
	public String greeting(@RequestParam(name="name", required=false, defaultValue="1") String name, Model model) {
		if (proxyUserInfoService == null) {
			proxyUserInfoService = (GetUserInfoService) new DynamicProxy(getUserInfoService).getProxy();
		}
		log.info(proxyUserInfoService.hashCode() + "");
		getUserInfoService.getUserInfoById(name, model);
		//这里返回的数据类型是String，但实际上更多的数据通过本函数中Model model传给了前端。返回值String也会被SpringMVC整合为一个ModelAndView，以供前端使用。（Controller可以返回多种数值，比如void、String、ModelAndView。同学们可以自行搜索学习）
		return "greeting";
	}
}
