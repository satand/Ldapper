package com.ldapper.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
public @interface LDAPAttribute {

	String name();

	String sup() default "";

	String[] aliases() default {};

	boolean ordered() default false;

	String description() default "";

	boolean operational() default false;

	boolean addToSchema() default false;

	String syntax() default "";

	String equality() default "";

	String ordering() default "";

	String substr() default "";
}
