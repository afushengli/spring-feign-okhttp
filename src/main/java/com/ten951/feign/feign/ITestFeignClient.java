package com.ten951.feign.feign;

import com.ten951.feign.configuration.FeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author Darcy
 * @date 2019-11-02 16:18
 */
@FeignClient(name = "testFeignClient", url = "http://localhost:8081/mybatis-read", configuration = FeignConfiguration.class)
public interface ITestFeignClient {


    /**
     *
     * @param orderId 单号
     * @return 信息
     */
    @RequestMapping(method = RequestMethod.GET, path = "/test/get",
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE, consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    String get(@RequestParam("orderId") Long orderId);
}
