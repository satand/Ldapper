package com.ldapper.context.template;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.apache.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.vfs.Vfs;
import org.reflections.vfs.Vfs.Dir;
import org.reflections.vfs.Vfs.UrlType;
import org.reflections.vfs.ZipDir;

import com.ldapper.annotations.LDAPAttribute;
import com.ldapper.annotations.LDAPNestedObject;
import com.ldapper.annotations.LDAPObject;
import com.ldapper.annotations.LDAPObjectClass;
import com.ldapper.annotations.LDAPObjectId;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;
import com.ldapper.data.SupportedObjType;
import com.ldapper.exception.IDAttributeNotFound;
import com.ldapper.exception.InvalidExternalObjException;
import com.ldapper.utility.LDAPUtilities;

public class ExternalObjTemplate {

	private static transient final Logger log = Logger
			.getLogger(ExternalObjTemplate.class);

	static {
		Vfs.addDefaultURLTypes(new UrlType() {

			@Override
			public boolean matches(URL url) {
				return url.getProtocol().equals("vfszip");
			}

			@Override
			public Dir createDir(URL url) {
				try {
					return new ZipDir(url.toString()
							.replaceFirst("vfszip:", "file:"));
				}
				catch (RuntimeException e) {
					log.error("Error: " + e.getMessage(), e);
					throw e;
				}
			}
		});
	}

	private static HashMap<Class<? extends LDAPPERBean>, ExternalObjTemplate> cache =
			new HashMap<Class<? extends LDAPPERBean>, ExternalObjTemplate>();

	private boolean isAbstract = false;
	private Map<String, ExternalObjTemplate> externalObjTemplateChilds;
	private String superiorLDAPObjectName;

	private Class<? extends LDAPPERBean> extObjClass;
	private LDAPObjectClass ldapObjClass;
	private String idKey;
	private ObjectTemplate objectTemplate;
	private List<AttributeTemplate> objAttributeTemplates;
	private ArrayList<AttributeTemplate> objAutomaticDataAttributeTemplates;
	private HashMap<String, AttributeTemplate> objAliasAttributeTemplateMap;
	private List<NestedObjTemplate> nestedObjTemplates;
	private Method getterBaseDN;
	private Constructor<? extends LDAPPERBean> defConstructor;

	public static ExternalObjTemplate getInstance(
			Class<? extends LDAPPERBean> externalObjClass) throws Exception {
		ExternalObjTemplate res = cache.get(externalObjClass);
		if (res == null) {
			res = new ExternalObjTemplate(externalObjClass);
			cache.put(externalObjClass, res);
		}
		return res;
	}

	public static ExternalObjTemplate getInstance(LDAPPERBean externalObj)
			throws Exception {
		return getInstance(externalObj.getClass());
	}

	public static boolean isValidExtObj(Class<? extends LDAPPERBean> externalObjClass) {
		try {
			getInstance(externalObjClass);
		}
		catch (Exception e) {
			return false;
		}
		return true;
	}

	public static boolean isValidExtObj(LDAPPERBean externalObj) {
		return isValidExtObj(externalObj.getClass());
	}

	private ExternalObjTemplate(Class<? extends LDAPPERBean> externalObjClass)
			throws Exception {
		init(externalObjClass);
	}

	@SuppressWarnings("unchecked")
	private void init(Class<? extends LDAPPERBean> extObjClass) throws Exception {

		// Set extObjClass
		this.extObjClass = extObjClass;

		// Get LDAPObjectClass
		ldapObjClass = extObjClass.getAnnotation(LDAPObjectClass.class);
		if (ldapObjClass == null) {
			throw new InvalidExternalObjException(
					"LDAPObjectClass annotation not found in '"
							+ extObjClass.getName() + "'");
		}

		// Get LDAPAttributes, LDAPObjectId and possible nested LDAPObjectClass
		objAttributeTemplates = new ArrayList<AttributeTemplate>();
		objAutomaticDataAttributeTemplates = new ArrayList<AttributeTemplate>(1);
		objAliasAttributeTemplateMap = new HashMap<String, AttributeTemplate>();
		nestedObjTemplates = new ArrayList<NestedObjTemplate>(1);
		LDAPAttribute attributeAnn;
		AttributeTemplate at;
		List<String> attrAliases;

		ArrayList<Field> fields = new ArrayList<Field>();
		Class<?> c = extObjClass;
		do {
			fields.addAll(Arrays.asList(c.getDeclaredFields()));
			c = c.getSuperclass();
		} while (c != null);

		for (Field field : fields) {
			at = null;

			// LDAP Attribute annotations stay on field
			attributeAnn = field.getAnnotation(LDAPAttribute.class);
			if (attributeAnn != null) {
				// Get attribute template
				at = new AttributeTemplate(attributeAnn, field, extObjClass);

				// Insert attribute template into Map<alias, attribute template>
				attrAliases = at.getAttributeAliases();
				for (String alias : attrAliases) {
					if (objAliasAttributeTemplateMap.containsKey(alias)) {
						throw new InvalidExternalObjException("Attribute alias '"
								+ alias + "' is repeated in '"
								+ extObjClass.getName() + "'");
					}
					objAliasAttributeTemplateMap.put(alias, at);
				}

				// Add attribute template to templates list
				objAttributeTemplates.add(at);

				// Add attribute template to list of automatic attribute
				// templates
				if (at.getBaseManagedObjType() == SupportedObjType.AUTOMATIC_DATA_SERIALIZABLE_AS_STRING) {
					objAutomaticDataAttributeTemplates.add(at);
				}
			}

			// LDAP ObjectId annotations stay on field
			if (field.getAnnotation(LDAPObjectId.class) != null) {
				if (idKey != null) {
					throw new InvalidExternalObjException(
							"LDAPObjectId annotation is present more than one in '"
									+ extObjClass.getName() + "'");
				}
				if (attributeAnn == null) {
					throw new InvalidExternalObjException(
							"LDAPObjectId annotation must always be present with LDAPAttribute annotation!");
				}

				if (at != null && at.attributeIsOperational()) {
					throw new InvalidExternalObjException(
							"LDAPObjectId annotation cannot be present on an operational attribute in '"
									+ extObjClass.getName() + "'");
				}

				idKey = attributeAnn.name();
			}

			// LDAP Object annotations stay on field
			LDAPObject objectAnn = field.getAnnotation(LDAPObject.class);
			if (objectAnn != null) {
				if (attributeAnn != null) {
					throw new InvalidExternalObjException(
							"LDAPObject annotation do not be present with LDAPAttribute annotation!");
				}

				if (objectTemplate != null) {
					throw new InvalidExternalObjException(
							"LDAPObject annotation is present more than one in '"
									+ extObjClass.getName() + "'");
				}

				// Get object template
				objectTemplate = new ObjectTemplate(objectAnn, field, extObjClass);
			}

			// LDAP NestedObject annotations stay on field
			if (field.getAnnotation(LDAPNestedObject.class) != null) {
				if (attributeAnn != null) {
					throw new InvalidExternalObjException(
							"LDAPNestedObject annotation do not be present with LDAPAttribute annotation!");
				}

				// Check if more "equal" nested object exist in external object.
				// Two nested object in a external object
				// are "equal" if they refer equal LDAP objectClass.
				NestedObjTemplate nestObjTemplate =
						new NestedObjTemplate(field, extObjClass);
				String nestedObjectClassName =
						nestObjTemplate.getExternalObjTemplate().getLDAPObjectName();
				for (NestedObjTemplate not : nestedObjTemplates) {
					if (not.getExternalObjTemplate().getLDAPObjectName()
							.equals(nestedObjectClassName)) {
						throw new InvalidExternalObjException("In '"
								+ extObjClass.getName()
								+ "' LDAPNestedObject '"
								+ nestObjTemplate.getExternalObjTemplate()
										.getExternalObjClass().getName()
								+ "' has an LDAP objectClass '"
								+ nestedObjectClassName
								+ "' equal to other LDAPNestedObject '"
								+ not.getExternalObjTemplate().getExternalObjClass()
										.getName() + "'!");
					}
				}
				nestedObjTemplates.add(nestObjTemplate);
			}
		}
		objAttributeTemplates = Collections.unmodifiableList(objAttributeTemplates);
		nestedObjTemplates = Collections.unmodifiableList(nestedObjTemplates);

		if (isAbstract = Modifier.isAbstract(extObjClass.getModifiers())) {
			Reflections reflections =
					new Reflections(new ConfigurationBuilder()
							.addUrls(ClasspathHelper.forClass(extObjClass))
							.setScanners(new TypeAnnotationsScanner())
							.useParallelExecutor());
			Set<Class<?>> annotatedClasses =
					reflections.getTypesAnnotatedWith(LDAPObjectClass.class);

			ExternalObjTemplate eot;
			externalObjTemplateChilds = new HashMap<String, ExternalObjTemplate>();
			for (Class<?> annotatedClass : annotatedClasses) {
				if (extObjClass != annotatedClass
						&& extObjClass.isAssignableFrom(annotatedClass)
						&& !Modifier.isAbstract(annotatedClass.getModifiers())) {
					eot = getInstance((Class<? extends LDAPPERBean>) annotatedClass);
					externalObjTemplateChilds.put(eot.getLDAPObjectName(), eot);
				}
			}
			externalObjTemplateChilds =
					Collections.unmodifiableMap(externalObjTemplateChilds);
		} else {
			// Get LDAPPERBean methods
			getterBaseDN = extObjClass.getMethod("getBaseDN");

			// Check on default constructor
			try {
				defConstructor = extObjClass.getDeclaredConstructor();
				if (!defConstructor.isAccessible()) {
					defConstructor.setAccessible(true);
				}
			}
			catch (Exception e) {
				throw new InvalidExternalObjException(
						"Default constructor not found in '" + extObjClass.getName()
								+ "'");
			}

			if (objAttributeTemplates.size() == 0) {
				throw new InvalidExternalObjException(
						"Not found any LDAPAttribute annotation in '"
								+ extObjClass.getName() + "'");
			}

			if (idKey == null) {
				throw new InvalidExternalObjException(
						"LDAPObjectId annotation not found in '"
								+ extObjClass.getName() + "'");
			}
		}

		Class<?> superclass = extObjClass.getSuperclass();
		while (superclass != null) {
			if (LDAPPERBean.class.isAssignableFrom(superclass)) {
				LDAPObjectClass ldapObjClass =
						superclass.getAnnotation(LDAPObjectClass.class);
				if (ldapObjClass != null) {
					superiorLDAPObjectName = ldapObjClass.name();
					break;
				}
			}
			superclass = superclass.getSuperclass();
		}
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public String getSuperiorLDAPObjectName() {
		return superiorLDAPObjectName;
	}

	public Map<String, ExternalObjTemplate> getExternalObjTemplateChilds() {
		return externalObjTemplateChilds;
	}

	public ExternalObjTemplate getExternalObjTemplateChild(String objectClassName) {
		if (externalObjTemplateChilds != null) {
			return externalObjTemplateChilds.get(objectClassName);
		}
		return null;
	}

	public boolean hasEmptyExternalObjTemplateChilds() {
		return externalObjTemplateChilds != null
				&& externalObjTemplateChilds.isEmpty();
	}

	public Class<? extends LDAPPERBean> getExternalObjClass() {
		return extObjClass;
	}

	public LDAPPERBean getExternalObjNewInstance() throws Exception {
		if (isAbstract) {
			throw new Exception(
					"Impossible to instantiate an instance because this object is abstract");
		}
		return defConstructor.newInstance();
	}

	public List<AttributeTemplate> getObjAttributesTemplates() {
		return objAttributeTemplates;
	}

	public List<NestedObjTemplate> getNestedObjTemplates() {
		return nestedObjTemplates;
	}

	public ObjectTemplate getObjTemplate() {
		return objectTemplate;
	}

	public LDAPPERBean createExternalObjFromIdAndBaseDN(Object idValue, String baseDN)
			throws Exception {
		// Create obj
		LDAPPERBean obj = getExternalObjNewInstance();
		obj.setBaseDN(baseDN);

		// Set obj id field
		AttributeTemplate at = getObjAttributeId();
		Attribute attr = at.getAttributeFromValue(idValue);
		at.setAttribute(obj, attr, null, false);

		return obj;
	}

	public String getLDAPObjectName() {
		return ldapObjClass.name();
	}

	public List<String> getLDAPObjectSup() {
		return new ArrayList<String>(Arrays.asList(ldapObjClass.sup()));
	}

	public List<String> getLDAPObjectAuxiliaryList() {
		return new ArrayList<String>(Arrays.asList(ldapObjClass.auxiliary()));
	}

	public boolean ldapObjectIsAddedToSchema() {
		return ldapObjClass.addToSchema();
	}

	public String getLDAPObjectDescription() {
		return ldapObjClass.description();
	}

	public String getLDAPObjectBaseDN(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		return (String) getterBaseDN.invoke(externalObj);
	}

	public String getLDAPObjectDN(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		StringBuilder buf = new StringBuilder();
		buf.append(getLDAPObjectId(externalObj));
		String objSubContextBase = getLDAPObjectBaseDN(externalObj);
		if (objSubContextBase.trim().length() > 0) {
			buf.append(',').append(objSubContextBase);
		}
		return buf.toString();
	}

	public String getLDAPObjectId(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		try {
			return objAliasAttributeTemplateMap.get(idKey).getAttributeForDN(
					externalObj);
		}
		catch (Exception e) {
			throw new IDAttributeNotFound("Not found ID attribute '" + idKey + "'");
		}
	}

	public List<ModificationItem> getModificationItem(LDAPPERBean externalObj,
			boolean removeUnspecifiedAttr, Attributes currAttrs) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		ArrayList<ModificationItem> result =
				new ArrayList<ModificationItem>(objAttributeTemplates.size());

		// Add all other attributes
		Attribute currAttr = null;
		Attribute newAttr;
		for (AttributeTemplate extObjAttribute : objAttributeTemplates) {
			if (extObjAttribute.attributeIsOperational()) {
				continue;
			}

			for (String alias : extObjAttribute.getAttributeAliases()) {
				currAttr = currAttrs.get(alias);
				if (currAttr != null) {
					break;
				}
			}

			// Get new attribute from external object
			newAttr =
					currAttr == null ? extObjAttribute.getAttribute(externalObj)
							: extObjAttribute.getAttribute(currAttr.getID(),
									externalObj);

			if (newAttr != null) {
				if (currAttr == null) {
					result.add(new ModificationItem(DirContext.ADD_ATTRIBUTE,
							newAttr));
				} else if (!LDAPUtilities.compareAttribute(currAttr, newAttr)) {
					result.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
							newAttr));
				}
			} else if (removeUnspecifiedAttr && currAttr != null) {
				result.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
						currAttr));
			}
		}

		return result;
	}

	public ArrayList<String> checkTargetAttributeNames(String[] targetAttributes) {
		ArrayList<String> checkedTA = new ArrayList<String>();
		if (targetAttributes == null) {
			if (!isAbstract) {
				for (AttributeTemplate at : objAttributeTemplates) {
					checkedTA.add(at.getAttributeName());
				}
			} else {
				return null;
			}
		} else {
			AttributeTemplate at;
			for (String tAttr : targetAttributes) {
				if ((at = getObjAttribute(tAttr)) == null) {
					log.debug("checkTargetAttributes - Attribute '" + tAttr
							+ "' not found in an object '" + extObjClass.getName()
							+ "'");
					continue;
				}
				checkedTA.add(at.getAttributeName());
			}
			if (isAbstract && !checkedTA.contains("objectclass")) {
				checkedTA.add("objectclass");
			}
		}
		return checkedTA;
	}

	public ArrayList<String> checkTargetAttributes(
			LDAPPERTargetAttribute[] targetAttributes) {
		if (targetAttributes == null) {
			return checkTargetAttributeNames(null);
		}

		String[] tas = new String[targetAttributes.length];
		for (int i = 0; i < targetAttributes.length; i++) {
			tas[i] = targetAttributes[i].getName();
		}
		return checkTargetAttributeNames(tas);
	}

	public ArrayList<NestedObjTemplate> checkTargetNestedObjs(
			String[] targetNestedObjs) {
		ArrayList<NestedObjTemplate> checkedTNOs;
		if (targetNestedObjs == null) {
			checkedTNOs = new ArrayList<NestedObjTemplate>(nestedObjTemplates);
		} else {
			checkedTNOs = new ArrayList<NestedObjTemplate>();
			NestedObjTemplate not;
			for (String tNO : targetNestedObjs) {
				if ((not = getNestedObj(tNO)) == null) {
					log.debug("checkTargetNestedObjs - Nested object with name '"
							+ tNO + "' not found in an object '"
							+ extObjClass.getName() + "'");
					continue;
				}
				checkedTNOs.add(not);
			}
		}
		return checkedTNOs;
	}

	public Attribute getAttribute(LDAPPERBean externalObj, String attrName)
			throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Get attribute
		return objAliasAttributeTemplateMap.get(attrName).getAttribute(externalObj);
	}

	public Attributes getAttributes(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		Attributes attributes = new BasicAttributes();

		// Add attribute objClasses
		attributes.put(getObjClassesAttribute());

		// Add all other attributes
		Attribute attr;
		for (AttributeTemplate extObjAttribute : objAttributeTemplates) {
			attr = extObjAttribute.getAttribute(externalObj);
			if (attr != null && attr.size() > 0) {
				attributes.put(attr);
			}
		}

		return attributes;
	}

	public Object getObject(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		if (objectTemplate == null) {
			return null;
		}
		return objectTemplate.getObject(externalObj);
	}

	public void setObject(LDAPPERBean externalObj, Object value) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		if (objectTemplate == null) {
			return;
		}
		objectTemplate.setObject(externalObj, value);
	}

	public void setAutomaticData(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Set all automatic data attributes
		for (AttributeTemplate extObjAttribute : objAutomaticDataAttributeTemplates) {
			extObjAttribute.setAutomaticData(externalObj);
		}
	}

	public void setAttribute(LDAPPERBean externalObj, Attribute attr,
			LDAPPERTargetAttribute targetAttr, boolean conservative)
			throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Set attribute
		AttributeTemplate at = objAliasAttributeTemplateMap.get(attr.getID());
		if (at != null) {
			at.setAttribute(externalObj, attr, targetAttr.getValueRegexList(),
					conservative);
		}
	}

	@SuppressWarnings("unchecked")
	public void setAttributes(LDAPPERBean externalObj, Attributes attrs,
			LDAPPERTargetAttribute[] targetAttrs, boolean conservative)
			throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Set attributes
		if (attrs != null) {
			NamingEnumeration<Attribute> en =
					(NamingEnumeration<Attribute>) attrs.getAll();
			Attribute attr;
			String attrID;
			AttributeTemplate at;
			List<String> attrValueRegexList = null;
			while (en.hasMore()) {
				attr = en.nextElement();
				attrID = attr.getID();

				at = objAliasAttributeTemplateMap.get(attrID);
				if (at != null) {
					if (targetAttrs != null) {
						// Get attrValueRegexList
						for (LDAPPERTargetAttribute ta : targetAttrs) {
							if (attrID.equals(ta.getName())) {
								attrValueRegexList = ta.getValueRegexList();
								break;
							}
						}
					}

					at.setAttribute(externalObj, attr, attrValueRegexList,
							conservative);
					attrValueRegexList = null;
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void removeAttributes(LDAPPERBean externalObj, Attributes attrs)
			throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Remove attributes
		if (attrs != null) {
			NamingEnumeration<Attribute> en =
					(NamingEnumeration<Attribute>) attrs.getAll();
			Attribute attr;
			String attrID;
			AttributeTemplate at;
			while (en.hasMore()) {
				attr = en.nextElement();
				attrID = attr.getID();

				at = objAliasAttributeTemplateMap.get(attrID);
				if (at != null) {
					at.removeAttribute(externalObj, attr);
				}
			}
		}
	}

	public LDAPPERBean filterDN(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Create new obj
		LDAPPERBean newExternalObj = getExternalObjNewInstance();

		// Set DN
		newExternalObj.setDN(externalObj.getDN());

		return newExternalObj;
	}

	public LDAPPERBean filterAttributes(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Create new obj
		LDAPPERBean newExternalObj = getExternalObjNewInstance();

		// Set DN
		newExternalObj.setDN(externalObj.getDN());

		// Set object if it exists
		if (objectTemplate != null) {
			objectTemplate.setObject(newExternalObj,
					objectTemplate.getObject(externalObj));
		}

		// Set attributes
		for (AttributeTemplate extObjAttribute : objAttributeTemplates) {
			extObjAttribute.getSetter().invoke(newExternalObj,
					extObjAttribute.getGetter().invoke(externalObj));
		}

		return newExternalObj;
	}

	public LDAPPERBean filterNestedObjs(LDAPPERBean externalObj) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		// Create new obj
		LDAPPERBean newExternalObj = getExternalObjNewInstance();

		// Set DN
		newExternalObj.setDN(externalObj.getDN());

		// Set nested objects
		for (NestedObjTemplate not : nestedObjTemplates) {
			not.getSetter().invoke(newExternalObj,
					not.getGetter().invoke(externalObj));
		}

		return newExternalObj;
	}

	public AttributeTemplate getObjAttribute(String attributeName) {
		return objAliasAttributeTemplateMap.get(attributeName);
	}

	public AttributeTemplate getObjAttributeId() {
		return objAliasAttributeTemplateMap.get(idKey);
	}

	public boolean hasObjAttributes(String attributeName) {
		return objAliasAttributeTemplateMap.containsKey(attributeName);
	}

	public String getDNAttributeName() {
		return idKey;
	}

	public Attribute getObjClassesAttribute() {
		Attribute objClasses = new BasicAttribute("objectclass");
		objClasses.add(ldapObjClass.name());
		for (String auxiliary : ldapObjClass.auxiliary()) {
			objClasses.add(auxiliary);
		}
		return objClasses;
	}

	public String addObjClassesToSearchFilter(String filter) {
		StringBuilder sb = new StringBuilder();
		sb.append("(&(objectClass=").append(ldapObjClass.name()).append(')');
		for (String auxiliary : ldapObjClass.auxiliary()) {
			sb.append("(objectClass=").append(auxiliary).append(')');
		}
		if (filter != null && filter.trim().length() > 0) {
			if (filter.charAt(0) != '(') {
				sb.append('(').append(filter).append(')');
			} else {
				sb.append(filter);
			}
		}
		sb.append(')');
		return sb.toString();
	}

	public Iterator<NestedObjTemplate> getNestedObjIterator() {
		return nestedObjTemplates.iterator();
	}

	public boolean hasNestedObjs() {
		return !nestedObjTemplates.isEmpty();
	}

	public NestedObjTemplate getNestedObj(String nestedObjName) {
		for (NestedObjTemplate not : nestedObjTemplates) {
			if (not.getName().equals(nestedObjName)) {
				return not;
			}
		}
		return null;
	}

	public Attributes getTargetAttributes(LDAPPERBean externalObj,
			List<? extends LDAPPERTargetAttribute> targetsAttrs,
			boolean complementary) throws Exception {
		if (externalObj.getClass() != extObjClass) {
			if (!isAbstract || !extObjClass.isAssignableFrom(externalObj.getClass())) {
				throw new InvalidExternalObjException("Invalid parameter object '"
						+ externalObj.getClass().getName() + "'. It must be a '"
						+ extObjClass.getName() + "'");
			}
		}

		Attributes attrs =
				complementary ? getAttributes(externalObj) : new BasicAttributes();

		if (targetsAttrs != null && targetsAttrs.size() > 0) {
			AttributeTemplate at;
			Attribute attr;
			String atName;
			List<String> valueRegexList;

			for (LDAPPERTargetAttribute target : targetsAttrs) {
				at = objAliasAttributeTemplateMap.get(target.getName());
				if (at == null) {
					continue;
				}

				// Get attribute
				attr = at.getAttribute(externalObj);
				if (attr == null) {
					continue;
				}

				atName = at.getAttributeName();
				if (complementary) {
					attrs.remove(atName);
				}

				valueRegexList = target.getValueRegexList();
				if (valueRegexList == null || valueRegexList.size() == 0) {
					if (!complementary && attr.size() > 0) {
						attrs.put(attr);
					}
				} else {
					Attribute tAttr = new BasicAttribute(atName);
					Enumeration<?> valueAttrEnum = attr.getAll();
					String value;
					while (valueAttrEnum.hasMoreElements()) {
						try {
							value = (String) valueAttrEnum.nextElement();
							for (String vRegex : valueRegexList) {
								if (complementary ? !value.matches(vRegex) : value
										.matches(vRegex)) {
									tAttr.add(value);
								}
							}
						}
						catch (ClassCastException e) {/* ... */
						}
					}

					if (tAttr.size() > 0) {
						attrs.put(tAttr);
					}
				}
			}
		}
		return attrs;
	}

}
