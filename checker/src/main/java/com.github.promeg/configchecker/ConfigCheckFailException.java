package com.github.promeg.configchecker;

/**
 * Created by guyacong on 2015/12/23.
 */
public class ConfigCheckFailException extends RuntimeException {
    public ConfigCheckFailException(String message) {
        super(message);
    }
}
