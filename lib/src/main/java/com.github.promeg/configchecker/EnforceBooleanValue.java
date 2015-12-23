package com.github.promeg.configchecker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by guyacong on 2015/12/23.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.CLASS)
public @interface EnforceBooleanValue {
    String flavor();
    String buildType();
    boolean value();
}
