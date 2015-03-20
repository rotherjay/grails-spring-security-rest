package com.odobo.grails.plugin.springsecurity.rest.token.storage

import groovy.util.logging.Slf4j
import org.springframework.core.convert.converter.Converter
import org.springframework.core.serializer.support.DeserializingConverter
import org.springframework.core.serializer.support.SerializingConverter
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService

@Slf4j
class RedisTokenStorageService implements TokenStorageService {

    def redisService
    UserDetailsService userDetailsService

    /** Expiration in seconds */
    Integer expiration = 3600

    private static final String PREFIX = "spring:security:token:"

    Converter<Object, byte[]> serializer = new SerializingConverter()
    Converter<byte[], Object> deserializer = new DeserializingConverter()

    @Override
    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Searching in Redis for UserDetails of token ${tokenValue}"

        byte[] userDetails
        redisService.withRedis { jedis ->
            String key = buildKey(tokenValue)
            userDetails = jedis.get(key.getBytes('UTF-8'))
            jedis.expire(key, expiration)
        }

        if (userDetails) {
            return deserialize(userDetails) as UserDetails
        } else {
            throw new TokenNotFoundException("Token ${tokenValue} not found")
        }

    }

    @Override
    void storeToken(String tokenValue, UserDetails principal) {
        log.debug "Storing principal for token: ${tokenValue} with expiration of ${expiration} seconds"
        log.debug "Principal: ${principal}"

        redisService.withRedis { jedis ->
            String key = buildKey(tokenValue)
            jedis.set(key.getBytes('UTF-8'), serialize(principal))
            jedis.expire(key, expiration)
        }
    }

    @Override
    void removeToken(String tokenValue) throws TokenNotFoundException {
        redisService.del(buildKey(tokenValue))
    }

    private static String buildKey(String token){
        "$PREFIX$token"
    }

    private Object deserialize(byte[] bytes) {
        if(!bytes) {
            return null
        } else {
            try {
                return deserializer.convert(bytes)
            } catch (Exception var3) {
                throw new Exception("Cannot deserialize", var3)
            }
        }
    }

    private byte[] serialize(Object object) {
        if(object == null) {
            return new byte[0]
        } else {
            try {
                return serializer.convert(object)
            } catch (Exception var3) {
                throw new Exception("Cannot serialize", var3)
            }
        }
    }

}
