package com.ldapper.schema.opends;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.bind.annotation.XmlElement;

import com.ldapper.annotations.LDAPObjectId;
import com.ldapper.context.template.AttributeTemplate;
import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.context.template.NestedObjTemplate;
import com.ldapper.data.LDAPPERBean;

public class LDAPPERBeanToLdapSchema {

	static final String OBJECT_OID = "1.3.6.1.4.1.X.2.3";
	static final String ATTRIBUTE_OID = "1.3.6.1.4.1.X.2.4";

	private int nextObjOid;
	private int nextAttrOid;

	private Map<Class<?>, SchemaObjectClass> schemaObjectClassesMap =
			new HashMap<Class<?>, SchemaObjectClass>();
	private Map<String, SchemaAttribute> schemaAttributeMap =
			new HashMap<String, SchemaAttribute>();

	public LDAPPERBeanToLdapSchema(Set<Class<? extends LDAPPERBean>> beanClassesSet,
			int startObjOid, int startAttrOid) throws Exception {
		nextObjOid = startObjOid;
		nextAttrOid = startAttrOid;

		// comincio il parsing
		for (Class<? extends LDAPPERBean> beanClass : beanClassesSet) {
			parse(beanClass);
		}
	}

	public Collection<SchemaAttribute> getSchemaAttributes() {
		TreeSet<SchemaAttribute> treeSet =
				new TreeSet<SchemaAttribute>(new Comparator<SchemaAttribute>() {

					@Override
					public int compare(SchemaAttribute att1, SchemaAttribute att2) {
						return att1.getOid().compareTo(att2.getOid());
					}
				});

		treeSet.addAll(schemaAttributeMap.values());
		return Collections.unmodifiableCollection(treeSet);
	}

	public Collection<SchemaObjectClass> getSchemaObjectClasses() {
		TreeSet<SchemaObjectClass> treeSet =
				new TreeSet<SchemaObjectClass>(new Comparator<SchemaObjectClass>() {

					@Override
					public int compare(SchemaObjectClass obj1, SchemaObjectClass obj2) {
						return obj1.getOid().compareTo(obj2.getOid());
					}
				});
		treeSet.addAll(schemaObjectClassesMap.values());

		return Collections.unmodifiableCollection(treeSet);
	}

	@SuppressWarnings("unchecked")
	private <T extends LDAPPERBean> void parse(Class<T> beanClass) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(beanClass);
		SchemaObjectClass schemaObjectClass = new SchemaObjectClass(eot);

		// controllo se classe già analizzata
		if (!schemaObjectClassesMap.containsKey(beanClass)) {
			Class<?> superclass = beanClass.getSuperclass();
			if (superclass != null && LDAPPERBean.class.isAssignableFrom(superclass)) {
				parse((Class<? extends LDAPPERBean>) superclass);
			}

			System.out.println("ObjectClass: " + schemaObjectClass.getName());

			if (eot.ldapObjectIsAddedToSchema()) {
				schemaObjectClass.setOid(nextObjOid());
				schemaObjectClassesMap.put(beanClass, schemaObjectClass);
			}

			// Ciclo sugli ldap attributes
			for (AttributeTemplate attributeTemplate : eot
					.getObjAttributesTemplates()) {

				System.out.println("Attribute: "
						+ attributeTemplate.getAttributeName());
				SchemaAttribute schemaAttribute =
						schemaAttributeMap.get(attributeTemplate.getAttributeName());

				// controllo se parametro già analizzato
				if (schemaAttribute == null) {
					schemaAttribute = new SchemaAttribute(attributeTemplate);

					if (attributeTemplate.attributeIsAddedToSchema()) {
						schemaAttribute.setOid(nextAttrOid());
						schemaAttributeMap.put(schemaAttribute.getName(),
								schemaAttribute);
					}
				}

				// controllo se must o may
				if (checkIfMust(attributeTemplate)) {
					schemaObjectClass.getMust().add(schemaAttribute.getName());
				} else {
					schemaObjectClass.getMay().add(schemaAttribute.getName());
				}
			}

			// ciclo sui nested
			for (NestedObjTemplate nestedObjTemplate : eot.getNestedObjTemplates()) {
				// richiamo ricorsivamente la funzione
				parse(nestedObjTemplate.getBaseManagedObjClass());
			}

		}

	}

	private <T extends LDAPPERBean> boolean checkIfMust(
			AttributeTemplate attributeTemplate) {
		// se è un ID torno true
		if (attributeTemplate.getField().isAnnotationPresent(LDAPObjectId.class)) {
			return true;
		}

		// check if present XmlElement annotation with required true
		XmlElement xmlElement =
				attributeTemplate.getField().getAnnotation(XmlElement.class);
		if (xmlElement != null && xmlElement.required()) {
			return true;
		}
		return false;
	}

	private String nextObjOid() {
		return OBJECT_OID.replace("X", String.valueOf(nextObjOid++));
	}

	private String nextAttrOid() {
		return ATTRIBUTE_OID.replace("X", String.valueOf(nextAttrOid++));
	}

}
