package com.ldapper.data;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import com.ldapper.data.extension.AutomaticDataSerializableAsString;
import com.ldapper.data.extension.DataSerializableAsString;

public enum SupportedObjType {
	STRING,
	BYTE_ARRAY,
	DATA_SERIALIZABLE_AS_STRING,
	BOOLEAN,
	ENUM,
	INTEGER,
	BYTE,
	SHORT,
	LONG,
	FLOAT,
	DOUBLE,
	CHAR,
	AUTOMATIC_DATA_SERIALIZABLE_AS_STRING,
	BIG_DECIMAL;
	
	private final static String UTF_8 = "UTF-8";
	
	public static SupportedObjType getSupportedObj(Class<?> objClass) 
	{
		if (objClass == String.class) {
			return STRING;
		}
		else if (objClass.isArray() && objClass.getComponentType() == byte.class) {
			return BYTE_ARRAY;
		}
		else if (AutomaticDataSerializableAsString.class.isAssignableFrom(objClass)) {
			return AUTOMATIC_DATA_SERIALIZABLE_AS_STRING;
		}
		else if (DataSerializableAsString.class.isAssignableFrom(objClass)) {
			return DATA_SERIALIZABLE_AS_STRING;
		}
		else if (objClass == Boolean.class) {
			return BOOLEAN;
		}
		else if (objClass.isEnum()) {
			return ENUM;
		}
		else if (objClass == Integer.class) {
			return INTEGER;
		}
		else if (objClass == Byte.class) {
			return BYTE;
		}
		else if (objClass == Short.class) {
			return SHORT;
		}
		else if (objClass == Long.class) {
			return LONG;
		}
		else if (objClass == Float.class) {
			return FLOAT;
		}
		else if (objClass == Double.class) {
			return DOUBLE;
		}
		else if (objClass == Character.class) {
			return CHAR;
		}
		else if (objClass == BigDecimal.class) {
			return BIG_DECIMAL;
		}
		return null;
	}

	public static Object convert(SupportedObjType supportedObjType, 
			Class<?> targetClass, Object obj) throws Exception
	{
		if (obj == null) {
			return null;
		}

		Class<?> startClass = obj.getClass();
		if (startClass == targetClass) {
			return obj;
		}

		switch (supportedObjType) {
			case STRING:
			case BYTE_ARRAY: {
				// Fix for password
				if (obj instanceof byte[]) {
					return new String((byte[]) obj, UTF_8);
				} 
				else {
					return String.valueOf(obj);
				}
			}
			case DATA_SERIALIZABLE_AS_STRING:
			case AUTOMATIC_DATA_SERIALIZABLE_AS_STRING: {
				// Fix for password
				String objValue; 
				if (obj instanceof byte[]) {
					objValue = new String((byte[]) obj, UTF_8);
				} 
				else {
					objValue = (String) obj;
				}

				DataSerializableAsString info = (DataSerializableAsString) targetClass.newInstance();
				info.write(objValue);
				return info;
			}
			case BOOLEAN: {
				return Boolean.valueOf(obj.toString());
			}
			case ENUM: {
				if (startClass != String.class) {
					throw new Exception("Conversion from '" + startClass.getName()
							+ "' to enum '" + targetClass.getName() + "' is not supported!");
				}
				
				try {
					Method m = targetClass.getMethod("fromValue", String.class);
					return m.invoke(null, obj);
				} 
				catch (Exception e) {
					if (e instanceof NoSuchMethodException) {
						throw new Exception(
								"Conversion to enum '" + targetClass.getName()
								+ "' not supported. Not found static method fromValue(String value) to obtain enum object!", e);
					}
					throw new Exception("Error converting '" + obj
							+ "' to enum '" + targetClass.getName() + "': "	+ e.getMessage(), e);
				}
			}
			case INTEGER : {
				return Integer.valueOf(obj.toString());
			}
			case BYTE : {
				return Byte.valueOf(obj.toString());
			}
			case SHORT : {
				return Short.valueOf(obj.toString());
			}
			case LONG : {
				return Long.valueOf(obj.toString());
			}
			case FLOAT : {
				return Float.valueOf(obj.toString());
			}
			case DOUBLE : {
				return Double.valueOf(obj.toString());
			}
			case CHAR : {
				return Character.valueOf(obj.toString().charAt(0));
			}
			case BIG_DECIMAL : {
				return BigDecimal.valueOf(Double.valueOf(obj.toString()));
			}
			default : {
				throw new Exception("Conversion from '" + startClass.getName()
						+ "' to '" + targetClass.getName() + "' not supported!");
			}
		}
	}

	public static Object convertForAttributeValue(SupportedObjType supportedObjType, Object obj)
	{
		switch (supportedObjType) {
			case STRING: {
				if (((String)obj).trim().isEmpty()) {
					return null;
				}
				return obj;
			}
			case BYTE_ARRAY: {
				if (((byte[])obj).length == 0) {
					return null;
				}
				return obj;
			}
			case DATA_SERIALIZABLE_AS_STRING:
			case AUTOMATIC_DATA_SERIALIZABLE_AS_STRING:{
				String value = ((DataSerializableAsString) obj).read();
				if (value != null && value.trim().isEmpty()) {
					return null;
				}
				return value;
			}
			case BOOLEAN: {
				return String.valueOf(obj).toUpperCase();
			}
			case ENUM: {
				return obj.toString();
			}
			default : {
				return String.valueOf(obj);
			}
		}
	}

}
