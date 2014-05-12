package com.ldapper.context.template;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.apache.commons.lang.StringUtils;

import com.ldapper.annotations.LDAPObject;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.exception.InvalidExternalObjException;

public class ObjectTemplate {

	private Field field;
	private Method setter;
	private Method getter;
	private LDAPObject ldapObject;
	
	public ObjectTemplate(LDAPObject ldapObject, Field field, Class<?> extObjClass) throws Exception
	{
		this.ldapObject = ldapObject;
		this.field = field;
		
		Class<?> managedObjClass = field.getType();
		
		// Find setter
		String capitalizedFieldName = StringUtils.capitalize(field.getName());
		try {
			setter = extObjClass.getMethod(
					"set".concat(capitalizedFieldName), managedObjClass);
		}
		catch (NoSuchMethodException e) {
			throw new InvalidExternalObjException(
					"Not found setter method correspondent to field '" +
					field.getName() + "'");
		}

		// Find getter
		try {
			getter = extObjClass.getMethod(
					"get".concat(capitalizedFieldName));
		}
		catch (NoSuchMethodException e) {
			try {
				getter = extObjClass.getMethod(
						"is".concat(capitalizedFieldName));
			} 
			catch (NoSuchMethodException e2) {
				throw new InvalidExternalObjException(
						"Not found getter method correspondent to field '" +
						field.getName() + "'");
			}
		}
		if (!managedObjClass.equals(getter.getReturnType())) {
			throw new InvalidExternalObjException(
					"Getter method '" + getter.getName() +
					"' don't have return type equals to type of field '" +
					field.getName() + "'!");
		}

		Type genericType = getter.getGenericReturnType();
		if (!setter.getGenericParameterTypes()[0].equals(genericType)) {
			throw new InvalidExternalObjException(extObjClass.getName() +
					": Returned value of getter method '" + getter.getName() +
					"' isn't equal to parameter of setter method '" + setter.getName() + "'");
		}
	}

	public Field getField() {
		return field;
	}

	public Method getSetter() {
		return setter;
	}

	public Method getGetter() {
		return getter;
	}

	public String getObjectDescription() {
		return ldapObject.description();
	}
	
	public Object getObject(LDAPPERBean externalObj) throws Exception {
		return getter.invoke(externalObj);
	}

	public void setObject(LDAPPERBean externalObj, Object value) throws Exception {
		setter.invoke(externalObj, value);
	}

}
