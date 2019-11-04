package com.ten951.feign.controller;

import com.ten951.feign.feign.ITestFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Darcy
 * @date 2019-11-02 16:27
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private ITestFeignClient feignClient;

    @RequestMapping(method = RequestMethod.GET, path = "/get")
    public String get(@RequestParam("orderId") Long orderId) {
        return feignClient.get(orderId);
    }
}
