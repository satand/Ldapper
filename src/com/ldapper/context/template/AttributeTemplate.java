package com.ldapper.context.template;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.ldap.Rdn;

import org.apache.commons.lang.StringUtils;

import com.ldapper.annotations.LDAPAttribute;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.ObjType;
import com.ldapper.data.SupportedObjType;
import com.ldapper.data.extension.AutomaticDataSerializableAsString;
import com.ldapper.data.extension.DataSerializableAsString;
import com.ldapper.data.extension.EntryMapDataSerializableAsString;
import com.ldapper.exception.InvalidAttributeException;
import com.ldapper.exception.InvalidExternalObjException;
import com.ldapper.utility.LDAPUtilities;

public class AttributeTemplate implements Comparable<AttributeTemplate> {

	private Field field;
	private Method setter;
	private Method getter;
	private LDAPAttribute ldapAttribute;
	private List<String> attributeAliases;

	private ObjType managedObjClassType;
	private Class<?> managedObjClass;

	private SupportedObjType baseManagedObjType;
	private Class<?> baseManagedObjClass;

	public AttributeTemplate(LDAPAttribute ldapAttribute, Field field,
			Class<?> extObjClass) throws Exception {
		this.ldapAttribute = ldapAttribute;
		this.field = field;
		managedObjClass = field.getType();
		managedObjClassType = ObjType.getObjType(managedObjClass);

		// Find setter
		String capitalizedFieldName = StringUtils.capitalize(field.getName());
		try {
			setter =
					extObjClass.getMethod("set".concat(capitalizedFieldName),
							managedObjClass);
		}
		catch (NoSuchMethodException e) {
			throw new InvalidExternalObjException(
					"Not found setter method correspondent to field '"
							+ field.getName() + "' in " + extObjClass.getName());
		}

		// Find getter
		try {
			getter = extObjClass.getMethod("get".concat(capitalizedFieldName));
		}
		catch (NoSuchMethodException e) {
			try {
				getter = extObjClass.getMethod("is".concat(capitalizedFieldName));
			}
			catch (NoSuchMethodException e2) {
				throw new InvalidExternalObjException(
						"Not found getter method correspondent to field '"
								+ field.getName() + "'  in " + extObjClass.getName());
			}
		}

		if (!managedObjClass.equals(getter.getReturnType())) {
			throw new InvalidExternalObjException("Getter method '"
					+ getter.getName()
					+ "' don't have return type equals to type of field '"
					+ field.getName() + "' in " + extObjClass.getName());
		}

		Type genericType = getter.getGenericReturnType();
		if (!setter.getGenericParameterTypes()[0].equals(genericType)) {
			throw new InvalidExternalObjException(extObjClass.getName()
					+ ": Returned value of getter method '" + getter.getName()
					+ "' isn't equal to parameter of setter method '"
					+ setter.getName() + "' in " + extObjClass.getName());
		}

		switch (managedObjClassType) {
			case SIMPLE: {
				baseManagedObjClass = managedObjClass;
				if (baseManagedObjClass.isPrimitive()) {
					throw new InvalidExternalObjException(extObjClass.getName()
							+ ": In " + extObjClass.getName()
							+ " simple attribute '" + ldapAttribute.name()
							+ "' with primitive class '"
							+ baseManagedObjClass.getName() + "' not supported!");
				}
				break;
			}
			case ARRAY: {
				baseManagedObjClass = managedObjClass.getComponentType();
				break;
			}
			case COLLECTION: {
				Type type = genericType;
				if (!(type instanceof ParameterizedType)) {
					throw new InvalidExternalObjException(extObjClass.getName()
							+ ": In " + extObjClass.getName()
							+ " returned value of getter method '"
							+ getter.getName()
							+ "' and parameter of setter method '"
							+ setter.getName()
							+ "' must be a parameterized collection!");
				}
				type = ((ParameterizedType) type).getActualTypeArguments()[0];
				if (!(type instanceof Class<?>)) {
					throw new InvalidExternalObjException(
							extObjClass.getName()
									+ ": In "
									+ extObjClass.getName()
									+ " returned value of getter method '"
									+ getter.getName()
									+ "' and parameter of setter method '"
									+ setter.getName()
									+ "' must be a parameterized collection of defined simple objects!");
				}
				baseManagedObjClass = (Class<?>) type;
				// Check if managedObjClass is List and in this case transform
				// this in ArrayList
				if (managedObjClass == List.class) {
					managedObjClass = ArrayList.class;
				}
				break;
			}
			case MAP: {
				Type type = genericType;
				if (!(type instanceof ParameterizedType)) {
					throw new InvalidExternalObjException(extObjClass.getName()
							+ ": In " + extObjClass.getName()
							+ " returned value of getter method '"
							+ getter.getName()
							+ "' and parameter of setter method '"
							+ setter.getName() + "' must be a parameterized map!");
				}
				Type[] types = ((ParameterizedType) type).getActualTypeArguments();
				for (int i = 0; i < 2; i++) {
					type = types[i];
					if (!(type instanceof Class<?>)) {
						throw new InvalidExternalObjException(
								extObjClass.getName()
										+ ": In "
										+ extObjClass.getName()
										+ " returned value of getter method '"
										+ getter.getName()
										+ "' and parameter of setter method '"
										+ setter.getName()
										+ "' must be a parameterized map of EntryMapDataSerializableAsString objects!");
					}
				}
				baseManagedObjClass = (Class<?>) types[1];
				if (!EntryMapDataSerializableAsString.class
						.isAssignableFrom(baseManagedObjClass)) {
					throw new InvalidExternalObjException(
							extObjClass.getName()
									+ ": In "
									+ extObjClass.getName()
									+ " returned value of getter method '"
									+ getter.getName()
									+ "' and parameter of setter method '"
									+ setter.getName()
									+ "' must be a parameterized map of EntryMapDataSerializableAsString objects!");
				}
				break;
			}
			default: {
				throw new InvalidExternalObjException(extObjClass.getName()
						+ ": In " + extObjClass.getName() + " type of field '"
						+ field.getName() + "' isn't supported!");
			}
		}

		baseManagedObjType = SupportedObjType.getSupportedObj(baseManagedObjClass);
		if (baseManagedObjType == null) {
			throw new InvalidExternalObjException(extObjClass.getName() + ": In "
					+ extObjClass.getName() + " type of field '" + field.getName()
					+ "' isn't supported. Class '" + baseManagedObjClass.getName()
					+ "' isn't managed!");
		}

		if (baseManagedObjType == SupportedObjType.DATA_SERIALIZABLE_AS_STRING
				|| baseManagedObjType == SupportedObjType.AUTOMATIC_DATA_SERIALIZABLE_AS_STRING) {
			// Check if baseManagedObjClass is not abstract
			if (Modifier.isAbstract(baseManagedObjClass.getModifiers())) {
				throw new InvalidExternalObjException("In " + extObjClass.getName()
						+ " attribute class '" + baseManagedObjClass.getName()
						+ "' " + " cannot be abstract!");
			}

			// Check if baseManagedObjClass has a default invoking constructor
			try {
				Constructor<?> defConstructor =
						baseManagedObjClass.getDeclaredConstructor();
				if (!defConstructor.isAccessible()) {
					defConstructor.setAccessible(true);
				}
			}
			catch (Exception e) {
				throw new InvalidExternalObjException("In " + extObjClass.getName()
						+ " attribute class '" + baseManagedObjClass.getName()
						+ "' don't have the default constructor");
			}
		}

		// Get attribute aliases
		attributeAliases = new ArrayList<String>(3);
		attributeAliases.add(ldapAttribute.name());
		attributeAliases.addAll(Arrays.asList(ldapAttribute.aliases()));
		attributeAliases = Collections.unmodifiableList(attributeAliases);
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

	public Class<?> getManagedObjClass() {
		return managedObjClass;
	}

	public ObjType getManagedObjClassType() {
		return managedObjClassType;
	}

	public SupportedObjType getBaseManagedObjType() {
		return baseManagedObjType;
	}

	public Class<?> getBaseManagedObjClass() {
		return baseManagedObjClass;
	}

	public String getAttributeName() {
		return ldapAttribute.name();
	}

	public boolean attributeIsOrdered() {
		return ldapAttribute.ordered();
	}

	public boolean attributeIsOperational() {
		return ldapAttribute.operational();
	}

	public boolean attributeIsAddedToSchema() {
		return ldapAttribute.addToSchema();
	}

	public String getAttributeDescription() {
		return ldapAttribute.description();
	}

	public String getSyntax() {
		return ldapAttribute.syntax();
	}

	public String getEquality() {
		return ldapAttribute.equality();
	}

	public String getOrdering() {
		return ldapAttribute.ordering();
	}

	public String getSubstr() {
		return ldapAttribute.substr();
	}

	public List<String> getAttributeAliases() {
		return attributeAliases;
	}

	public String getSup() {
		return ldapAttribute.sup();
	}

	public Attribute getEmptyAttribute() {
		return new BasicAttribute(ldapAttribute.name(), ldapAttribute.ordered());
	}

	public Attribute getEmptyAttribute(String attributeAlias) {
		if (!attributeAliases.contains(attributeAlias)) {
			throw new RuntimeException("Alias '" + attributeAlias
					+ "' is not valid for attribute '" + ldapAttribute.name() + "'");
		}
		return new BasicAttribute(attributeAlias, ldapAttribute.ordered());
	}

	public Attribute getAttributeFromValue(Object value) {
		return new BasicAttribute(ldapAttribute.name(), value,
				ldapAttribute.ordered());
	}

	public Attribute getAttributeFromValue(String attributeAlias, Object value) {
		if (!attributeAliases.contains(attributeAlias)) {
			throw new RuntimeException("Alias '" + attributeAlias
					+ "' is not valid for attribute '" + ldapAttribute.name() + "'");
		}
		return new BasicAttribute(attributeAlias, value, ldapAttribute.ordered());
	}

	public Attribute getAttribute(LDAPPERBean externalObj) throws Exception {
		return getAttribute(ldapAttribute.name(), externalObj);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Attribute getAttribute(String attributeAlias, LDAPPERBean externalObj)
			throws Exception {
		if (!attributeAliases.contains(attributeAlias)) {
			throw new RuntimeException("Alias '" + attributeAlias
					+ "' is not valid for attribute '" + ldapAttribute.name() + "'");
		}

		if (ldapAttribute.operational()) {
			return null;
		}

		Object value = getter.invoke(externalObj);
		if (value == null) {
			return null;
		}

		BasicAttribute attr = null;
		switch (managedObjClassType) {
			case SIMPLE: {
				attr = new BasicAttribute(attributeAlias, ldapAttribute.ordered());
				addValueToAttribute(attr, value);

				// Check if attribute is empty
				if (attr.size() == 0) {
					attr = null;
				}
				break;
			}
			case ARRAY: {
				Object[] values = (Object[]) value;
				if (values.length > 0) {
					attr =
							new BasicAttribute(attributeAlias,
									ldapAttribute.ordered());
					for (Object v : values) {
						if (v == null) {
							continue;
						}
						addValueToAttribute(attr, v);
					}

					// Check if attribute is empty
					if (attr.size() == 0) {
						attr = null;
					}
				}
				break;
			}
			case COLLECTION: {
				Collection<Object> values = (Collection<Object>) value;
				if (!values.isEmpty()) {
					attr =
							new BasicAttribute(attributeAlias,
									ldapAttribute.ordered());
					for (Object v : values) {
						if (v == null) {
							continue;
						}
						addValueToAttribute(attr, v);
					}

					// Check if attribute is empty
					if (attr.size() == 0) {
						attr = null;
					}
				}
				break;
			}
			case MAP: {
				Map values = (Map) value;
				if (!values.isEmpty()) {
					attr =
							new BasicAttribute(attributeAlias,
									ldapAttribute.ordered());
					for (Object v : values.values()) {
						if (v == null) {
							continue;
						}
						addValueToAttribute(attr, v);
					}

					// Check if attribute is empty
					if (attr.size() == 0) {
						attr = null;
					}
				}
				break;
			}
		}
		return attr;
	}

	public String getAttributeForDN(LDAPPERBean externalObj) throws Exception {
		return getAttributeForDN(ldapAttribute.name(), externalObj);
	}

	public String getAttributeForDN(String attributeAlias, LDAPPERBean externalObj)
			throws Exception {
		Attribute attr = getAttribute(attributeAlias, externalObj);
		if (attr == null || attr.size() == 0) {
			throw new Exception("Not found attribute '" + attributeAlias
					+ "' in external object");
		}

		StringBuilder buf = new StringBuilder();
		buf.append(attr.getID()).append('=').append(Rdn.escapeValue(attr.get()));
		return buf.toString();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setAttribute(LDAPPERBean externalObj, Attribute attribute,
			List<String> attrValueRegexList, boolean conservative) throws Exception {
		if (!attribute.getID().equals(ldapAttribute.name())
				&& attributeAliases != null
				&& !attributeAliases.contains(attribute.getID())) {
			throw new InvalidAttributeException("Invalid parameter attribute '"
					+ attribute.getID() + "'. It must be " + ldapAttribute.name());
		}

		if (!attribute.getID().equals(ldapAttribute.name())
				&& attributeAliases != null
				&& !attributeAliases.contains(attribute.getID())) {
			throw new InvalidAttributeException("Invalid parameter attribute '"
					+ attribute.getID() + "'. It must be " + ldapAttribute.name());
		}

		if (conservative && managedObjClassType != ObjType.SIMPLE) {
			// Merge new and current attributes
			Attribute currAttr = getAttribute(attribute.getID(), externalObj);
			if (currAttr != null && currAttr.size() > 0) {
				if (managedObjClassType == ObjType.MAP
						|| EntryMapDataSerializableAsString.class
								.isAssignableFrom(baseManagedObjClass)) {
					attribute =
							LDAPUtilities.mergeAttribute(attribute, currAttr,
									baseManagedObjType, baseManagedObjClass);
				} else {
					attribute = LDAPUtilities.mergeAttribute(attribute, currAttr);
				}
			}
		}

		Object value;
		switch (managedObjClassType) {
			case SIMPLE: {
				value =
						SupportedObjType.convert(baseManagedObjType,
								baseManagedObjClass, attribute.get());
				if (checkAttrValue(baseManagedObjType, value, attrValueRegexList)) {
					setter.invoke(externalObj, value);
				}
				break;
			}
			case ARRAY: {
				ArrayList<Object> setParam = new ArrayList<Object>();
				for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {
					value =
							SupportedObjType.convert(baseManagedObjType,
									baseManagedObjClass, e.next());
					if (checkAttrValue(baseManagedObjType, value, attrValueRegexList)) {
						setParam.add(value);
					}
				}
				Object obj = Array.newInstance(baseManagedObjClass, setParam.size());
				System.arraycopy(setParam.toArray(), 0, obj, 0, setParam.size());

				setter.invoke(externalObj, obj);
				break;
			}
			case COLLECTION: {
				Collection setParam = (Collection) managedObjClass.newInstance();
				for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {
					value =
							SupportedObjType.convert(baseManagedObjType,
									baseManagedObjClass, e.next());
					if (checkAttrValue(baseManagedObjType, value, attrValueRegexList)) {
						setParam.add(value);
					}
				}
				setter.invoke(externalObj, setParam);
				break;
			}
			case MAP: {
				Map setParam = (Map) managedObjClass.newInstance();
				for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {
					value =
							SupportedObjType.convert(baseManagedObjType,
									baseManagedObjClass, e.next());
					if (checkAttrValue(baseManagedObjType, value, attrValueRegexList)) {
						setParam.put(
								((EntryMapDataSerializableAsString) value).getKey(),
								value);
					}
				}
				setter.invoke(externalObj, setParam);
				break;
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void removeAttribute(LDAPPERBean externalObj, Attribute attribute)
			throws Exception {
		if (!attribute.getID().equals(ldapAttribute.name())
				&& attributeAliases != null
				&& !attributeAliases.contains(attribute.getID())) {
			throw new InvalidAttributeException("Invalid parameter attribute '"
					+ attribute.getID() + "'. It must be " + ldapAttribute.name());
		}

		if (attribute.size() == 0 || managedObjClassType == ObjType.SIMPLE) {
			Object value = null;
			setter.invoke(externalObj, value);
			return;
		}

		Object currValue = getter.invoke(externalObj);
		if (currValue == null) {
			return;
		}

		switch (managedObjClassType) {
			case ARRAY: {
				Object value;
				Object[] values = (Object[]) currValue;
				if (values.length > 0) {
					ArrayList<Object> setParam = new ArrayList<Object>();
					for (Object v : values) {
						setParam.add(v);
					}
					// Remove indicated attribute values from current values
					for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {
						setParam.remove(SupportedObjType.convert(baseManagedObjType,
								baseManagedObjClass, e.next()));
					}

					value = Array.newInstance(baseManagedObjClass, setParam.size());
					System.arraycopy(setParam.toArray(), 0, value, 0,
							setParam.size());
				} else {
					value = values;
				}
				setter.invoke(externalObj, value);
				break;
			}
			case COLLECTION: {
				Collection<Object> values = (Collection<Object>) currValue;
				if (values.size() > 0) {
					// Remove indicated attribute values from current values
					for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {
						values.remove(SupportedObjType.convert(baseManagedObjType,
								baseManagedObjClass, e.next()));
					}
				}
				setter.invoke(externalObj, values);
				break;
			}
			case MAP: {
				Map map = (Map) currValue;
				if (map.size() > 0) {
					// Remove indicated attribute values from current values
					Collection values = map.values();
					for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {
						values.remove(SupportedObjType.convert(baseManagedObjType,
								baseManagedObjClass, e.next()));
					}
				}
				setter.invoke(externalObj, map);
				break;
			}
		}
	}

	private void addValueToAttribute(Attribute attr, Object value) {
		Object attrValue =
				SupportedObjType.convertForAttributeValue(baseManagedObjType, value);
		if (attrValue != null) {
			attr.add(attrValue);
		}
	}

	private boolean checkAttrValue(SupportedObjType supportedObjType, Object obj,
			List<String> attrValueRegexList) {
		if (attrValueRegexList == null || attrValueRegexList.size() == 0) {
			return true;
		}

		String value;
		switch (supportedObjType) {
			case STRING: {
				value = (String) obj;
				break;
			}
			case DATA_SERIALIZABLE_AS_STRING:
			case AUTOMATIC_DATA_SERIALIZABLE_AS_STRING: {
				value = ((DataSerializableAsString) obj).read();
				break;
			}
			default: {
				return true;
			}
		}

		if (value != null) {
			for (String regex : attrValueRegexList) {
				if (value.matches(regex)) {
					return true;
				}
			}
		}
		return false;
	}

	public void setAutomaticData(LDAPPERBean externalObj) throws Exception {
		// Check if attribute is a AutomaticDataSerializableAsString and not an
		// operational attribute
		if (baseManagedObjType != SupportedObjType.AUTOMATIC_DATA_SERIALIZABLE_AS_STRING
				|| ldapAttribute.operational()) {
			return;
		}

		// Set new attribute value
		AutomaticDataSerializableAsString field;
		Object attrObj = getter.invoke(externalObj);
		if (attrObj != null) {
			field = (AutomaticDataSerializableAsString) attrObj;
		} else {
			field =
					(AutomaticDataSerializableAsString) baseManagedObjClass
							.newInstance();
		}
		field.createAutomaticValue(externalObj);
		setter.invoke(externalObj, field);
	}

	@Override
	public int compareTo(AttributeTemplate o) {
		return ldapAttribute.name().compareTo(o.ldapAttribute.name());
	}
}
