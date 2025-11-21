package com.leo.leopicturebackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC Json 配置
 */
@JsonComponent
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance) // 把 Long 类型序列化为 String (框架默认实现了，开启即可，此处是通过配置类，也可以从配置文件开启)
                    .deserializerByType(Long.class, new StringToLongDeserializer()) // 把 String 类型反序列化为 Long
                    .deserializerByType(Long.TYPE, new StringToLongDeserializer()); // 把 String 类型反序列化为 long
        };
    }
}

