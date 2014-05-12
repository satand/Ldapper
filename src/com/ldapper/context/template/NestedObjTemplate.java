package com.ldapper.context.template;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.ldapper.annotations.LDAPObjectClass;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.ObjType;
import com.ldapper.exception.InvalidExternalObjException;

public class NestedObjTemplate {
	
	private Field field;
	private Method setter;
	private Method getter;
	private ExternalObjTemplate selfExtObjTemplate;
	private String name;


	private ObjType managedObjClassType;
	private Class<?> managedObjClass;
	
	private Class<? extends LDAPPERBean> baseManagedObjClass;
	
	@SuppressWarnings("unchecked")
	public NestedObjTemplate(Field field, Class<?> extObjClass) throws Exception
	{
		this.field = field;
		this.name = field.getName();
		managedObjClass = field.getType();
		managedObjClassType = ObjType.getObjType(managedObjClass);
		
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
		
		Class<?> tempBaseManagedObjClass;
		switch (managedObjClassType) {
			case SIMPLE: {
				tempBaseManagedObjClass = managedObjClass;
				break;
			}
			case ARRAY: {
				tempBaseManagedObjClass = managedObjClass.getComponentType();
				break;
			}
			case COLLECTION: {
				Type type = genericType;
				if (!(type instanceof ParameterizedType)) {
					throw new InvalidExternalObjException(extObjClass.getName() +
							": Returned value of getter method '" + getter.getName() +
							"' and parameter of setter method '" + setter.getName() +
							"' must be a parameterized collection!");
				}
				type = ((ParameterizedType)type).getActualTypeArguments()[0];
				if (!(type instanceof Class<?>)) {
					throw new InvalidExternalObjException(extObjClass.getName() +
							": Returned value of getter method '" + getter.getName() +
							"' and parameter of setter method '" +	setter.getName() +
							"' must be a parameterized collection of defined simple objects!");
				}
				tempBaseManagedObjClass = (Class<?>) type;
				if (tempBaseManagedObjClass.getAnnotation(LDAPObjectClass.class) == null) {
					throw new InvalidExternalObjException(extObjClass.getName() +
							": Returned value of getter method '" + getter.getName() +
							"' and parameter of setter method '" +	setter.getName() +
							"' must be a parameterized collection of LDAPObjectClass annotated objects!");
				}
				// Check if managedObjClass is List and in this case transform this in ArrayList 
				if (managedObjClass == List.class) {
					managedObjClass = ArrayList.class;
				}
				break;
			}
			default: {
				throw new InvalidExternalObjException(extObjClass.getName() +
						": Returned value of getter method '" + getter.getName() +
						"' and parameter of setter method '" + setter.getName() +
						"' isn't managed!");				
			}
		}

		try {
			baseManagedObjClass = (Class<? extends LDAPPERBean>) tempBaseManagedObjClass;
		}
		catch (ClassCastException e) {
			throw new InvalidExternalObjException(extObjClass.getName() +
					": Returned value of getter method '" + getter.getName() +
					"' and parameter of setter method '" +	setter.getName() +
					"' must be a simple object, a parameterized array or collection of objects implementing LDAPPERBean interface!");
		}

		selfExtObjTemplate = ExternalObjTemplate.getInstance(baseManagedObjClass);
	}
	
	public Class<?> getManagedObjClass() {
		return managedObjClass;
	}

	public Class<? extends LDAPPERBean> getBaseManagedObjClass() throws Exception {
		return baseManagedObjClass;
	}

	public LDAPPERBean getExternalObjNewInstance() throws Exception {
		return selfExtObjTemplate.getExternalObjNewInstance();
	}
	
	public ExternalObjTemplate getExternalObjTemplate() {
		return selfExtObjTemplate;
	}
	
	public String getName() {
		return name;
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
	
	@SuppressWarnings("unchecked")
	public LDAPPERBean[] getNestedObj(LDAPPERBean externalObj) throws Exception {
		Object result = getter.invoke(externalObj);
		if (result != null) {
			switch (managedObjClassType) {
				case SIMPLE: {
					return new LDAPPERBean[]{ (LDAPPERBean) result };
				}
				case ARRAY: {
					return (LDAPPERBean[]) result;
				}
				case COLLECTION: {
					return ((Collection<LDAPPERBean>) result).toArray(new LDAPPERBean[0]);
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setNestedObj(Object externalObj, List<?> values) throws Exception {
		switch (managedObjClassType) {
			case SIMPLE: {
				setter.invoke(externalObj, values.isEmpty() ? null : values.get(0));	
				break;
			}
			case ARRAY: {
				Object[] ob = (Object[]) Array.newInstance(baseManagedObjClass, values.size());
				for (int i = 0; i < values.size(); i++) {
					ob[i] = values.get(i);
				}
				setter.invoke(externalObj, (Object)ob);
				break;
			}
			case COLLECTION: {
				Collection setParam = (Collection) managedObjClass.newInstance();
				setParam.addAll(values);
				setter.invoke(externalObj, setParam);
				break;
			}
		}
	}

}
