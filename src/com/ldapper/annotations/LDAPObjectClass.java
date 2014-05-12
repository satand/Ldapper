package com.ldapper.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface LDAPObjectClass {

	String name();

	String[] sup() default {};

	String[] auxiliary() default {};

	String description() default "";

	boolean addToSchema() default false;
}
