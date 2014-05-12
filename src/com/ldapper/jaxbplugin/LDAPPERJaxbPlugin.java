package com.ldapper.jaxbplugin;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.FormParam;
import javax.xml.validation.Schema;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import com.ldapper.annotations.LDAPAttribute;
import com.ldapper.annotations.LDAPNestedObject;
import com.ldapper.annotations.LDAPObject;
import com.ldapper.annotations.LDAPObjectClass;
import com.ldapper.annotations.LDAPObjectId;
import com.ldapper.data.LDAPPERBeanToXML;
import com.ldapper.data.extension.DataSerializableAsString;
import com.ldapper.exception.InvalidBaseDN;
import com.ldapper.exception.InvalidDN;
import com.ldapper.utility.LDAPUtilities;
import com.ldapper.utility.XMLUtils;
import com.sun.codemodel.ClassType;
import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCatchBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JEnumConstant;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.XJCFacade;
import com.sun.tools.xjc.generator.bean.field.IsSetField;
import com.sun.tools.xjc.generator.bean.field.UntypedListField;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.EnumConstantOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSTerm;

public class LDAPPERJaxbPlugin extends Plugin {

	private static final String NAME_SPACE = "http://ldapper.italtel.com";

	private static final Pattern PARAM_PATTERN = Pattern
			.compile("\\$\\[([^\\$\\[\\]]+)\\]");

	public static enum LDAPPER_ANNOTATION {
		LDAPObjectClass,
		LDAPAttribute,
		LDAPAttributeId,
		LDAPObject,
		LDAPNestedObject
	};

	private static enum OPERATION_TYPE {
		SERIALIZABLE,
		DATA_SERIALIZABLE_AS_STRING
	};

	@Override
	public String getOptionName() {
		return "Xldapper";
	}

	@Override
	public String getUsage() {
		return "  -Xldapper          :  inject specified Java annotation according to LDAPPER template";
	}

	@Override
	public boolean isCustomizationTagName(String nsUri, String localName) {
		return nsUri.equals(NAME_SPACE);
	}

	@Override
	public List<String> getCustomizationURIs() {
		return Collections.singletonList(NAME_SPACE);
	}

	@Override
	public boolean run(Outline model, Options opt, ErrorHandler errorHandler)
			throws SAXException {
		for (ClassOutline co : model.getClasses()) {
			try {
				// create utility methods
				createToStringMethod(co.implClass);
				createGetBooleanMethod(co.implClass);
				createFillDefaultValuesMethod(co);

				createToXmlMethod(co.implClass);
				createPrintXmlMethod(co.implClass);
				createFromXmlMethods(co.implClass);
				createFromDocumentMethod(co.implClass);
				createToDocumentMethod(co.implClass);
				createValidateXmlMethod(co.implClass);
				createCollectionSetters(co);

				Iterator<CPluginCustomization> iterator =
						co.target.getCustomizations().iterator();

				CPluginCustomization pluginCustomization;
				boolean hasLdapperObjectClassAnnotation = false;
				boolean hasLdapperAttributeIdAnnotation = false;
				while (iterator.hasNext()) {
					pluginCustomization = iterator.next();
					pluginCustomization.markAsAcknowledged();

					OPERATION_TYPE operation_type = null;
					try {
						operation_type =
								OPERATION_TYPE.valueOf(pluginCustomization.element
										.getLocalName());
					}
					catch (Exception e) {
					}

					if (operation_type != null) {
						if (operation_type == OPERATION_TYPE.SERIALIZABLE) {
							serializable(co.implClass,
									pluginCustomization.element.getAttribute("uid"));
						} else if (operation_type == OPERATION_TYPE.DATA_SERIALIZABLE_AS_STRING) {
							String helperClass =
									pluginCustomization.element
											.getAttribute("helperClass");
							if (helperClass == null
									|| helperClass.trim().length() == 0) {
								throw new RuntimeException(
										"HelperClass cannot be empty!");
							}
							String argsValue =
									pluginCustomization.element.getAttribute("args");
							String[] args = null;
							if (argsValue != null && argsValue.trim().length() > 0) {
								args = argsValue.split(",");
							}

							dataSerializableAsString(co.implClass, helperClass,
									args,
									pluginCustomization.element
											.getAttribute("pattern"),
									pluginCustomization.element
											.getAttribute("format"));
						}
						continue;
					}

					LDAPPER_ANNOTATION ldapper_annotation;
					try {
						ldapper_annotation =
								LDAPPER_ANNOTATION
										.valueOf(pluginCustomization.element
												.getLocalName());
					}
					catch (Exception e) {
						continue;
					}

					JAnnotationUse annotationUse = null;

					if (ldapper_annotation == LDAPPER_ANNOTATION.LDAPObjectClass) {
						hasLdapperObjectClassAnnotation = true;

						if (!co.implClass.owner().ref(LDAPPERBeanToXML.class)
								.isAssignableFrom(co.implClass._extends())) {
							co.implClass._implements(LDAPPERBeanToXML.class);
						}

						// Add LDAPObjectClass annotation
						annotationUse = co.implClass.annotate(LDAPObjectClass.class);
						// Add name
						String name =
								pluginCustomization.element.getAttribute("name");
						if (name == null || name.trim().length() == 0) {
							name = StringUtils.uncapitalize(co.implClass.name());
						}
						annotationUse.param("name", name);
						// Add superior
						String superior =
								pluginCustomization.element.getAttribute("sup");
						if (superior != null && superior.trim().length() > 0) {
							String[] values = superior.split(",");
							if (values.length > 0) {
								JAnnotationArrayMember arrayMember =
										annotationUse.paramArray("sup");
								for (String v : values) {
									arrayMember.param(v);
								}
							}
						}
						// Add auxiliary
						String auxiliary =
								pluginCustomization.element
										.getAttribute("auxiliary");
						if (auxiliary != null && auxiliary.trim().length() > 0) {
							String[] values = auxiliary.split(",");
							if (values.length > 0) {
								JAnnotationArrayMember arrayMember =
										annotationUse.paramArray("auxiliary");
								for (String v : values) {
									arrayMember.param(v);
								}
							}
						}
						// Add description
						String desc =
								pluginCustomization.element.getAttribute("desc");
						if (desc != null && desc.trim().length() > 0) {
							annotationUse.param("description", desc);
						}
						// Add addToSchema
						boolean isAddToSchema = false;
						String addToSchema =
								pluginCustomization.element
										.getAttribute("addToSchema");
						if (addToSchema != null && addToSchema.trim().length() > 0) {
							isAddToSchema = Boolean.valueOf(addToSchema);
							annotationUse.param("addToSchema", isAddToSchema);
						}

						createClassDoc(co.implClass, desc, name, auxiliary,
								isAddToSchema);
					} else if (ldapper_annotation == LDAPPER_ANNOTATION.LDAPAttribute
							|| ldapper_annotation == LDAPPER_ANNOTATION.LDAPAttributeId) {
						JFieldVar field =
								getField(co.implClass, pluginCustomization);

						// Add LDAPAttribute annotation
						annotationUse = field.annotate(LDAPAttribute.class);
						// Add name
						String attrName =
								pluginCustomization.element.getAttribute("name");
						if (attrName == null || attrName.trim().length() == 0) {
							attrName = StringUtils.uncapitalize(field.name());
						}
						annotationUse.param("name", attrName);
						// Add superior
						String superior =
								pluginCustomization.element.getAttribute("sup");
						if (superior != null && superior.trim().length() > 0) {
							annotationUse.param("sup", superior);
						}
						// Add aliases
						String aliases =
								pluginCustomization.element.getAttribute("aliases");
						if (aliases != null && aliases.trim().length() > 0) {
							String[] values = aliases.split(",");
							if (values.length > 0) {
								JAnnotationArrayMember arrayMember =
										annotationUse.paramArray("aliases");
								for (String v : values) {
									arrayMember.param(v.trim());
								}
							}
						}
						// Add ordered
						boolean isOrdered = false;
						String param =
								pluginCustomization.element.getAttribute("ordered");
						if (param != null && param.trim().length() > 0) {
							isOrdered = Boolean.valueOf(param);
							annotationUse.param("ordered", isOrdered);
						}
						// Add operational
						boolean isOperational = false;
						param =
								pluginCustomization.element
										.getAttribute("operational");
						if (param != null && param.trim().length() > 0) {
							isOperational = Boolean.valueOf(param);
							annotationUse.param("operational", isOperational);
						}
						// Add default value
						param = pluginCustomization.element.getAttribute("default");
						if (param != null && param.trim().length() > 0) {
							setDefaultValue(co.implClass, field, param);
						}
						// Add description
						String desc =
								pluginCustomization.element.getAttribute("desc");
						if (desc != null && desc.trim().length() > 0) {
							annotationUse.param("description", desc);
						}
						// Add addToSchema
						boolean isAddToSchema = false;
						param =
								pluginCustomization.element
										.getAttribute("addToSchema");
						if (param != null && param.trim().length() > 0) {
							isAddToSchema = Boolean.valueOf(param);
							annotationUse.param("addToSchema", isAddToSchema);
						}
						// Add syntax
						param = pluginCustomization.element.getAttribute("syntax");
						if (param != null && param.trim().length() > 0) {
							annotationUse.param("syntax", param);
						}
						// Add equality
						param = pluginCustomization.element.getAttribute("equality");
						if (param != null && param.trim().length() > 0) {
							annotationUse.param("equality", param);
						}
						// Add ordering
						param = pluginCustomization.element.getAttribute("ordering");
						if (param != null && param.trim().length() > 0) {
							annotationUse.param("ordering", param);
						}
						// Add substr
						param = pluginCustomization.element.getAttribute("substr");
						if (param != null && param.trim().length() > 0) {
							annotationUse.param("substr", param);
						}

						createReferenceToAttributeName(co.implClass, field.name(),
								attrName);

						boolean isAttributeId =
								ldapper_annotation == LDAPPER_ANNOTATION.LDAPAttributeId;
						if (isAttributeId) {
							if (isOperational) {
								throw new Exception("Attribute ID '" + attrName
										+ "' cannot be an operational attribute");
							}

							hasLdapperAttributeIdAnnotation = true;

							String parameterizedBaseDN =
									pluginCustomization.element
											.getAttribute("parameterizedBaseDN");

							// Add LDAPObjectId annotation
							annotationUse = field.annotate(LDAPObjectId.class);

							// Add IBaseDN methods and constructors
							createBaseDNMethodsAndConstructors(co.implClass, field,
									parameterizedBaseDN);

							// Add IDN methods
							createDNMethods(co.implClass, attrName, field);

							// Add equals and hashCode method
							createEqualsAndHashCodeMethod(co.implClass, field);
						}

						// Add FormParam annotation
						annotationUse = field.annotate(FormParam.class);
						annotationUse.param("value",
								StringUtils.uncapitalize(field.name()));

						createAttributeDoc(field, desc, attrName, aliases,
								isAttributeId, isOrdered, isOperational,
								isAddToSchema);
					} else if (ldapper_annotation == LDAPPER_ANNOTATION.LDAPNestedObject) {
						JFieldVar field =
								getField(co.implClass, pluginCustomization);

						// Add LDAPNestedObject annotation
						annotationUse = field.annotate(LDAPNestedObject.class);

						createNestedObjDoc(field);

						createReferenceToNestedObjName(co.implClass,
								getField(co.implClass, pluginCustomization).name());
					} else if (ldapper_annotation == LDAPPER_ANNOTATION.LDAPObject) {
						JFieldVar field =
								getField(co.implClass, pluginCustomization);

						// Add LDAPObject annotation
						annotationUse = field.annotate(LDAPObject.class);

						// Add description
						String desc =
								pluginCustomization.element.getAttribute("desc");
						if (desc != null && desc.trim().length() > 0) {
							annotationUse.param("description", desc);
						}

						createObjectDoc(field, desc);

						// Create reference to implicit javaClassName attribute
						createReferenceToAttributeName(co.implClass,
								"ldapObjectClassName", "javaClassName");
					}
				}

				if (hasLdapperObjectClassAnnotation
						&& !hasLdapperAttributeIdAnnotation
						&& !co.implClass.isAbstract()) {
					// Create static getInstance method from base DN
					createMethodGetInstanceFromBaseDN(co.implClass);
					// Create static getInstance method from DN
					createMethodGetInstanceFromDN(co.implClass);
				}

				createCloneMethod(co.implClass);
			}
			catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e.getMessage(), e);
			}
		}
		return true;
	}

	private void setDefaultValue(JDefinedClass implClass, JFieldVar field,
			String defaultValue) {
		// Search params in default value
		ArrayList<String> params = new ArrayList<String>();
		Matcher m = PARAM_PATTERN.matcher(defaultValue);
		while (m.find()) {
			// Get the match result
			params.add(m.group(1));
		}

		if (params.size() > 0) {
			boolean startWithParam = false;
			if (defaultValue.startsWith("$[" + params.get(0) + "]")) {
				startWithParam = true;
			} else {
				defaultValue = "\"" + defaultValue;
			}

			boolean endWithParam = false;
			if (defaultValue.endsWith("$[" + params.get(params.size() - 1) + "]")) {
				endWithParam = true;
			} else {
				defaultValue = defaultValue + "\"";
			}

			String param;
			for (int i = 0; i < params.size(); i++) {
				param = params.get(i);
				String newValue = "";

				if (i > 0 || i == 0 && !startWithParam) {
					newValue = "\" + ";
				}

				newValue += "get" + StringUtils.capitalize(param) + "()";

				if (i != params.size() - 1 || !endWithParam) {
					newValue += " + \"";
				}

				defaultValue =
						defaultValue
								.replaceFirst("\\$\\[" + param + "\\]", newValue);
			}
		}

		String getMethodName = "get" + StringUtils.capitalize(field.name());
		for (JMethod method : implClass.methods()) {
			if (method.name().equals(getMethodName)) {
				JBlock body = method.body();
				body.pos(0);
				body._if(field.eq(JExpr._null()))._then()
						._return(JExpr.direct(defaultValue));
				break;
			}
		}
	}

	public static JFieldVar getField(JDefinedClass implClass,
			CPluginCustomization pluginCustomization) {
		String fieldName = pluginCustomization.element.getAttribute("field");
		if (fieldName == null || fieldName.trim().length() == 0) {
			throw new RuntimeException("Not found field attribute in '"
					+ pluginCustomization + "' in '" + implClass + "'");
		}

		return getFieldFromName(implClass, fieldName);
	}

	public static JFieldVar getFieldFromName(JDefinedClass implClass,
			String fieldName) {
		JClass jclass = implClass;

		JFieldVar fieldVar = null;
		do {
			try {
				fieldVar = ((JDefinedClass) jclass).fields().get(fieldName);
				if (fieldVar != null) {
					break;
				}
				jclass = jclass._extends();
			}
			catch (Exception e) {
				break;
			}
		} while (jclass != null);

		if (fieldVar == null) {
			throw new RuntimeException("Not found field '" + fieldName + "' in '"
					+ implClass + "'");
		}
		return fieldVar;
	}

	private void createEqualsAndHashCodeMethod(JDefinedClass implClass,
			JFieldVar idVar) {
		JCodeModel codeModel = implClass.owner();

		JMethod equalsMethod =
				implClass.method(JMod.PUBLIC, codeModel.BOOLEAN, "equals");
		com.sun.codemodel.JVar that = equalsMethod.param(Object.class, "that");
		equalsMethod.annotate(Override.class);
		JTryBlock tryBlock = equalsMethod.body()._try();
		JCast jCast = new JCast(implClass, that);
		tryBlock.body()._return(
				JExpr._this().invoke("getDN").invoke("equals")
						.arg(jCast.invoke("getDN")));
		tryBlock._catch(codeModel.ref(Exception.class)).body()._return(JExpr.FALSE);

		JMethod hashCodeMethod =
				implClass.method(JMod.PUBLIC, codeModel.INT, "hashCode");
		hashCodeMethod.annotate(Override.class);
		tryBlock = hashCodeMethod.body()._try();
		tryBlock.body()._return(JExpr._this().invoke("getDN").invoke("hashCode"));
		tryBlock._catch(codeModel.ref(Exception.class)).body()._return(JExpr.lit(0));
	}

	private void createPrintXmlMethod(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod method = implClass.method(JMod.PUBLIC, codeModel.VOID, "printXml");
		JVar var = method.param(OutputStream.class, "stream");
		method.body().staticInvoke(codeModel.ref(XMLUtils.class), "printXml")
				.arg(JExpr._this()).arg(var);

		JDocComment doc = method.javadoc();
		doc.add("Write object xml format on an OutputStream");
		doc.addParam(var.name() + " allowed object is " + getLinkDoc(var.type()));
	}

	private void createToXmlMethod(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod method =
				implClass.method(JMod.PUBLIC, codeModel.ref(String.class), "toXml");
		method.body()._return(
				codeModel.ref(XMLUtils.class).staticInvoke("toXml")
						.arg(JExpr._this()));

		JDocComment doc = method.javadoc();
		doc.add("Get object in xml format");
		doc.addReturn()
				.add("XML object format, possible object is "
						+ getLinkDoc(method.type()));
	}

	private void createFromXmlMethods(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod method =
				implClass.method(JMod.PUBLIC | JMod.STATIC, implClass, "fromXml");
		JVar var1 = method.param(String.class, "xml");
		JVar var2 = method.param(Schema.class, "schema");
		method.body()._return(
				codeModel.ref(XMLUtils.class).staticInvoke("fromXml").arg(var1)
						.arg(JExpr.dotclass(implClass)).arg(var2));

		JDocComment doc = method.javadoc();
		doc.add("Get object from his xml format and schema");
		doc.addParam(var2.name() + " allowed object is " + getLinkDoc(var2.type()));
		doc.addParam(var1.name() + " allowed object is " + getLinkDoc(var1.type()));
		doc.addReturn().add("possible object is " + getLinkDoc(implClass));

		method = implClass.method(JMod.PUBLIC | JMod.STATIC, implClass, "fromXml");
		var1 = method.param(InputStream.class, "xml");
		var2 = method.param(Schema.class, "schema");
		method.body()._return(
				codeModel.ref(XMLUtils.class).staticInvoke("fromXml").arg(var1)
						.arg(JExpr.dotclass(implClass)).arg(var2));

		doc = method.javadoc();
		doc.add("Get object from his xml InputStream and schema");
		doc.addParam(var2.name() + " allowed object is " + getLinkDoc(var2.type()));
		doc.addParam(var1.name() + " allowed object is " + getLinkDoc(var1.type()));
		doc.addReturn().add("possible object is " + getLinkDoc(implClass));
	}

	private void createToDocumentMethod(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod method =
				implClass.method(JMod.PUBLIC, codeModel.ref(Document.class),
						"toDocument");
		method.body()._return(
				codeModel.ref(XMLUtils.class).staticInvoke("toDocument")
						.arg(JExpr._this()));

		JDocComment doc = method.javadoc();
		doc.add("Get object in document format");
		doc.addReturn().add(
				"Document object format, possible object is "
						+ getLinkDoc(method.type()));
	}

	private void createFromDocumentMethod(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod method =
				implClass.method(JMod.PUBLIC | JMod.STATIC, implClass,
						"fromDocument");
		JVar var1 = method.param(Document.class, "doc");
		JVar var2 = method.param(Schema.class, "schema");
		method.body()._return(
				codeModel.ref(XMLUtils.class).staticInvoke("fromDocument").arg(var1)
						.arg(JExpr.dotclass(implClass)).arg(var2));

		JDocComment doc = method.javadoc();
		doc.add("Get object from his document format and schema");
		doc.addParam(var2.name() + " allowed object is " + getLinkDoc(var2.type()));
		doc.addParam(var1.name() + " allowed object is " + getLinkDoc(var1.type()));
		doc.addReturn().add("possible object is " + getLinkDoc(implClass));
	}

	private void createValidateXmlMethod(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod method =
				implClass.method(JMod.PUBLIC, codeModel.VOID, "validateXml");
		method._throws(Exception.class);
		JVar var = method.param(Schema.class, "schema");
		method.body().invoke("fromXml").arg(JExpr.invoke("toXml")).arg(var);
		method.body().staticInvoke(codeModel.ref(XMLUtils.class), "validate")
				.arg(JExpr._this());
	}

	private void createReferenceToAttributeName(JDefinedClass implClass,
			String fieldName, String attributeName) {
		JCodeModel codeModel = implClass.owner();
		// Create reference to attributes names
		JFieldVar field =
				implClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
						codeModel.ref(String.class),
						"Attribute_" + StringUtils.capitalize(fieldName),
						JExpr.lit(attributeName));

		field.javadoc().add(
				"Static reference to LDAP attribute name relative to field "
						+ fieldName);
	}

	private void createReferenceToNestedObjName(JDefinedClass implClass,
			String fieldName) {
		JCodeModel codeModel = implClass.owner();

		// Create reference to nested object name
		JFieldVar field =
				implClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
						codeModel.ref(String.class),
						"NestedObject_" + StringUtils.capitalize(fieldName),
						JExpr.lit(fieldName));

		field.javadoc().add("Static reference to name of NestedObject " + fieldName);
	}

	private void createBaseDNMethodsAndConstructors(JDefinedClass implClass,
			JFieldVar idVar, String paramBaseDN) {
		JCodeModel codeModel = implClass.owner();

		String setterId = "set" + StringUtils.capitalize(idVar.name());

		// Create base DN pattern
		JFieldVar baseDNPatternField =
				implClass.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL,
						codeModel.ref(Pattern.class), "BASE_DN_PATTERN");

		// Create base DN setter method
		JMethod setBaseDN =
				implClass.method(JMod.PUBLIC, codeModel.VOID, "setBaseDN");
		JVar setBaseDNParam = setBaseDN.param(String.class, "baseDN");
		JDocComment doc = setBaseDN.javadoc();
		doc.add("Set object LDAP BaseDN (parental LDAP DN)");
		doc.addParam(setBaseDNParam.name()
				+ " object LDAP BaseDN, allowed object is "
				+ getLinkDoc(setBaseDNParam.type()));
		doc.addThrows(Exception.class).add("in case of invalid LDAP BaseDN");

		// Create BaseDN field
		JFieldVar baseDNField =
				implClass.field(JMod.PRIVATE | JMod.TRANSIENT,
						codeModel.ref(String.class), "baseDN", JExpr._null());
		baseDNField.javadoc().add("Object LDAP BaseDN");

		// Create base DN getter method
		JMethod getBaseDN =
				implClass.method(JMod.PUBLIC, codeModel.ref(String.class),
						"getBaseDN");
		getBaseDN._throws(InvalidBaseDN.class);
		doc = getBaseDN.javadoc();
		doc.add("Get object LDAP BaseDN (parental LDAP DN)");
		doc.addReturn().add("possible object is " + getLinkDoc(getBaseDN.type()));
		doc.addThrows(InvalidBaseDN.class).add("in case of incomplete LDAP BaseDN");

		// Add JsonIgnore annotation
		getBaseDN.annotate(JsonIgnore.class);

		JBlock getBaseDNThenBlock =
				getBaseDN.body()._if(baseDNField.eq(JExpr._null()))._then();

		// Get base DN params
		ArrayList<String> params = new ArrayList<String>();
		if (paramBaseDN != null && paramBaseDN.trim().length() > 0) {
			Matcher m = PARAM_PATTERN.matcher(paramBaseDN);
			while (m.find()) {
				// Get the match result
				params.add(m.group(1));
			}
		}

		// Caso bean senza parametri nel baseDN
		if (params.size() == 0) {
			// Initialize base DN pattern
			if (paramBaseDN != null && paramBaseDN.trim().length() > 0) {
				baseDNPatternField.init(codeModel.ref(Pattern.class)
						.staticInvoke("compile").arg(paramBaseDN));
			} else {
				baseDNPatternField.init(JExpr._null());
			}

			// Set setBaseDN method body
			setBaseDN.body()._return();

			// Set getBaseDN method body
			if (paramBaseDN == null || paramBaseDN.trim().length() == 0) {
				getBaseDNThenBlock.assign(baseDNField, JExpr.lit(""));
			} else {
				getBaseDNThenBlock.assign(baseDNField, JExpr.lit(paramBaseDN));
			}

			// Create constructor with Id attribute as parameter
			JMethod constructor = implClass.constructor(JMod.PUBLIC);
			constructor.param(idVar.type(), idVar.name());
			constructor.body().invoke(setterId).arg(idVar);

			doc = constructor.javadoc();
			doc.add("Creates a " + implClass.name()
					+ " from his LDAP attribute ID value");
			doc.addParam(idVar.name()
					+ " LDAP attribute ID value, allowed object is "
					+ getLinkDoc(idVar.type()));
		} else {
			// Initialize base DN pattern
			String baseDNPatternString = paramBaseDN;
			for (String param : params) {
				baseDNPatternString =
						baseDNPatternString.replaceFirst("\\$\\[" + param + "\\]",
								"([^=,\\\\+]+)");
			}
			baseDNPatternField.init(codeModel.ref(Pattern.class)
					.staticInvoke("compile").arg(baseDNPatternString));

			// Set getBaseDN method body
			JInvocation invoc =
					codeModel.ref(LDAPUtilities.class).staticInvoke("getBaseDN")
							.arg(baseDNPatternField);
			JFieldVar paramField;
			for (String param : params) {
				paramField = implClass.fields().get(param);

				// Add FormParam annotation
				JAnnotationUse annotationUse = paramField.annotate(FormParam.class);
				annotationUse.param("value",
						StringUtils.uncapitalize(paramField.name()));

				// Modify idVar setter method
				JMethod setterParam = getSetterMethod(implClass, paramField);
				setterParam.body().assign(JExpr._this().ref(baseDNField),
						JExpr._null());
				setterParam.body().assign(JExpr._this().ref("dn"), JExpr._null());

				// Add args to invocation
				invoc.arg(paramField);
			}
			getBaseDNThenBlock.assign(baseDNField, invoc);

			// Set setBaseDN method body
			setBaseDN._throws(InvalidBaseDN.class);
			JVar baseDNParts =
					setBaseDN.body().decl(
							codeModel.ref(String.class).array(),
							"baseDNParts",
							codeModel.ref(LDAPUtilities.class)
									.staticInvoke("getBaseDNParts")
									.arg(baseDNPatternField).arg(setBaseDNParam));
			for (int i = 0; i < params.size(); i++) {
				setBaseDN.body()
						.invoke("set" + StringUtils.capitalize(params.get(i)))
						.arg(baseDNParts.component(JExpr.lit(i)));
			}

			// Create constructor with base DN parts as parameters
			JMethod constructor1 = implClass.constructor(JMod.PUBLIC);
			JDocComment doc1 = constructor1.javadoc();
			doc1.add("Creates a " + implClass.name()
					+ " from his LDAP BaseDN parts (parental LDAP DN parts)");
			// Create constructor with Id attribute value and base DN parts as
			// parameters
			JMethod constructor2 = implClass.constructor(JMod.PUBLIC);
			JDocComment doc2 = constructor2.javadoc();
			doc2.add("Creates a "
					+ implClass.name()
					+ " from his LDAP attribute ID value and LDAP BaseDN parts (parental LDAP DN parts)");

			// Set constructors bodies
			constructor2.param(idVar.type(), idVar.name());
			doc2.addParam(idVar.name()
					+ " LDAP attribute ID value, allowed object is "
					+ getLinkDoc(idVar.type()));
			constructor2.body().invoke(setterId).arg(idVar);
			JVar var;
			for (String param : params) {
				var = constructor1.param(String.class, param);
				constructor1.body().invoke("set" + StringUtils.capitalize(param))
						.arg(var);
				doc1.addParam(var.name()
						+ " LDAP BaseDN part value, allowed object is "
						+ getLinkDoc(var.type()));

				var = constructor2.param(String.class, param);
				constructor2.body().invoke("set" + StringUtils.capitalize(param))
						.arg(var);
				doc2.addParam(var.name()
						+ " LDAP BaseDN part value, allowed object is "
						+ getLinkDoc(var.type()));
			}

			if (!implClass.isAbstract()) {
				// Create static getInstance method from base DN
				createMethodGetInstanceFromBaseDN(implClass);
			}
		}
		getBaseDN.body()._return(baseDNField);

		// Create default constructor
		JMethod constructor = implClass.constructor(JMod.PUBLIC);
		constructor.body().invoke("super");

		doc = constructor.javadoc();
		doc.add("Creates a empty " + implClass.name());
	}

	private void createDNMethods(JDefinedClass implClass, String attrIdName,
			JFieldVar idVar) {
		JCodeModel codeModel = implClass.owner();

		// Create ATTRIBUTE_ID_NAME
		JFieldVar AttrIdNameField =
				implClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL,
						codeModel.ref(String.class), "ATTRIBUTE_ID_NAME",
						JExpr.lit(attrIdName));
		AttrIdNameField.javadoc().add("Static reference to LDAP attribute ID name");

		// Create DN field
		JFieldVar dnField =
				implClass.field(JMod.PRIVATE | JMod.TRANSIENT,
						codeModel.ref(String.class), "dn", JExpr._null());
		dnField.javadoc().add("Object LDAP DN");

		// Create DN getter method
		JMethod getDN =
				implClass.method(JMod.PUBLIC, codeModel.ref(String.class), "getDN");
		getDN._throws(InvalidDN.class);
		getDN.body()
				._if(dnField.eq(JExpr._null()))
				._then()
				.assign(dnField,
						codeModel
								.ref(LDAPUtilities.class)
								.staticInvoke("getDN")
								.arg(AttrIdNameField)
								.arg(JExpr.invoke("get"
										+ StringUtils.capitalize(idVar.name())))
								.arg(JExpr.invoke("getBaseDN")));
		getDN.body()._return(dnField);

		// Add JsonIgnore annotation
		getDN.annotate(JsonIgnore.class);

		JDocComment doc = getDN.javadoc();
		doc.add("Get object LDAP DN");
		doc.addReturn().add("possible object is " + getLinkDoc(getDN.type()));
		doc.addThrows(InvalidDN.class).add("in case of incomplete LDAP DN");

		// Modify idVar setter method
		JMethod setterIdVar = getSetterMethod(implClass, idVar);
		setterIdVar.body().assign(JExpr._this().ref(dnField), JExpr._null());

		// Create DN setter method
		JMethod setDN = implClass.method(JMod.PUBLIC, codeModel.VOID, "setDN");
		JVar dn = setDN.param(String.class, "dn");
		setDN._throws(InvalidDN.class);
		JVar dnParts =
				setDN.body().decl(
						codeModel.ref(Object.class).array(),
						"dnParts",
						codeModel.ref(LDAPUtilities.class)
								.staticInvoke("getDNParts").arg(dn)
								.arg(JExpr.dotclass((JClass) idVar.type()))
								.arg(AttrIdNameField)
								.arg(JExpr.ref("BASE_DN_PATTERN")));

		JCast jCast = new JCast(idVar.type(), dnParts.component(JExpr.lit(0)));
		setDN.body().invoke("set" + StringUtils.capitalize(idVar.name())).arg(jCast);
		jCast =
				new JCast(codeModel.ref(String.class), dnParts.component(JExpr
						.lit(1)));
		setDN.body().invoke("setBaseDN").arg(jCast);

		doc = setDN.javadoc();
		doc.add("Set object LDAP DN");
		doc.addParam(dn.name() + " object LDAP DN, allowed object is "
				+ getLinkDoc(dn.type()));
		doc.addThrows(InvalidDN.class).add("in case of invalid LDAP DN");

		if (!implClass.isAbstract()) {
			// Create static getInstance method from DN
			createMethodGetInstanceFromDN(implClass);
		}
	}

	public static void createMethodGetInstanceFromBaseDN(JDefinedClass implClass) {
		// Create static getInstance method from base DN
		JMethod getInstanceFromBaseDN =
				implClass.method(JMod.PUBLIC | JMod.STATIC, implClass,
						"getInstanceFromBaseDN");
		JVar arg = getInstanceFromBaseDN.param(String.class, "baseDN");
		getInstanceFromBaseDN._throws(InvalidBaseDN.class);
		JVar instance =
				getInstanceFromBaseDN.body().decl(implClass,
						StringUtils.uncapitalize(implClass.name()),
						JExpr._new(implClass));
		getInstanceFromBaseDN.body().invoke(instance, "setBaseDN").arg(arg);
		getInstanceFromBaseDN.body()._return(instance);

		JDocComment doc = getInstanceFromBaseDN.javadoc();
		doc.add("Creates a " + implClass.name()
				+ " from his LDAP BaseDN (parental LDAP DN)");
		doc.addParam(arg.name() + " object LDAP BaseDN, allowed object is "
				+ getLinkDoc(arg.type()));
		doc.addThrows(InvalidBaseDN.class).add("in case of invalid LDAP BaseDN");
	}

	public static void createMethodGetInstanceFromDN(JDefinedClass implClass) {
		JMethod getInstanceFromDN =
				implClass.method(JMod.PUBLIC | JMod.STATIC, implClass,
						"getInstanceFromDN");
		JVar arg = getInstanceFromDN.param(String.class, "dn");
		getInstanceFromDN._throws(InvalidDN.class);
		JVar instance =
				getInstanceFromDN.body().decl(implClass,
						StringUtils.uncapitalize(implClass.name()),
						JExpr._new(implClass));
		getInstanceFromDN.body().invoke(instance, "setDN").arg(arg);
		getInstanceFromDN.body()._return(instance);

		JDocComment doc = getInstanceFromDN.javadoc();
		doc.add("Get object instance from his LDAP DN");
		doc.addParam(arg.name() + " object LDAP DN, allowed object is "
				+ getLinkDoc(arg.type()));
		doc.addReturn().add("possible object is " + getLinkDoc(implClass));
		doc.addThrows(InvalidDN.class).add("in case of invalid LDAP DN");
	}

	private void createCollectionSetters(ClassOutline co) {
		FieldOutline[] fos = co.getDeclaredFields();
		if (fos != null && fos.length > 0) {
			for (FieldOutline fo : fos) {
				if (fo instanceof UntypedListField || fo instanceof IsSetField
						&& ((IsSetField) fo).getPropertyInfo().isCollection()) {
					createCollectionSetter(co.implClass, fo.getPropertyInfo());
				}
			}
		}
	}

	/**
	 * Declares the <tt>setter</tt> method for Collection based properties.
	 */
	private void createCollectionSetter(JDefinedClass implClass, CPropertyInfo prop) {
		// Retrieve the Map that contains the field name and the corresponding
		// JFieldVar
		Map<String, JFieldVar> fields = implClass.fields();
		// Obtains a reference to the collection-based field
		JFieldVar field = fields.get(prop.getName(false));

		// Check if method already exists
		if (implClass.getMethod("set" + prop.getName(true),
				new JType[] { field.type() }) != null) {
			return;
		}

		JCodeModel codemodel = implClass.owner();
		// Creates the method
		JMethod $set =
				implClass.method(JMod.PUBLIC, codemodel.VOID,
						"set" + prop.getName(true));
		// Creates the JVar that will hold the method's parameter
		JVar $value = $set.param(field.type(), prop.getName(false));
		// Creates the body
		$set.body().assign(JExpr._this().ref(field), $value);

		JDocComment doc = $set.javadoc();
		doc.add("Sets the value of the " + field.name());
		doc.addParam($value.name() + " allowed object is "
				+ getLinkDoc($value.type()));
	}

	private void createAttributeDoc(JFieldVar field, String desc,
			String ldapAttributeName, String ldapAttributeAliasName,
			boolean isAttributeId, boolean isOrdered, boolean isOperational,
			boolean isAddToSchema) {
		StringBuilder sb = new StringBuilder();
		sb.append("<p><b>LDAP attribute name: </b>").append(ldapAttributeName);
		if (isAttributeId) {
			sb.append(" <b>(is attribute id on DN)</b>");
		}
		sb.append("</p>\n");
		if (ldapAttributeAliasName != null
				&& ldapAttributeAliasName.trim().length() > 0) {
			sb.append("<p><b>LDAP attribute alias: </b>" + ldapAttributeAliasName
					+ "</p>\n");
		}
		sb.append("<p><b>LDAP attribute is ordered: </b>" + isOrdered + "</p>\n");
		sb.append("<p><b>LDAP attribute is operational (read only): </b>"
				+ isOperational + "</p>\n");
		if (!isAddToSchema) {
			sb.append("<p><b>LDAP attribute is already present in core schema</b></p>\n");
		}
		if (desc != null && desc.trim().length() > 0) {
			sb.append("<p><b>Description: </b>").append(desc).append("</p>\n");
		}
		sb.append("<p>&nbsp;</p>\n");

		JDocComment doc = field.javadoc();
		doc.add(0, sb.toString());
	}

	private void createObjectDoc(JFieldVar field, String desc) {
		StringBuilder sb = new StringBuilder();
		sb.append("<p><b>LDAP object</b>").append("</p>\n");
		if (desc != null && desc.trim().length() > 0) {
			sb.append("<p><b>Description: </b>").append(desc).append("</p>\n");
		}
		sb.append("<p>&nbsp;</p>\n");

		JDocComment doc = field.javadoc();
		doc.add(0, sb.toString());
	}

	private void createNestedObjDoc(JFieldVar field) {
		StringBuilder sb = new StringBuilder();
		sb.append("<p><b>Nested Object</b></p>\n");
		sb.append(getLinkDoc(field.type())).append("\n\n");

		JDocComment doc = field.javadoc();
		doc.add(0, sb.toString());
	}

	private void createClassDoc(JDefinedClass dClass, String desc,
			String ldapObjClassName, String ldapAuxiliaryClassName,
			boolean isAddToSchema) {
		StringBuilder sb = new StringBuilder();
		sb.append("<p><b>LDAP obiectClass name: </b>").append(ldapObjClassName)
				.append("</p>\n");
		if (ldapAuxiliaryClassName != null
				&& ldapAuxiliaryClassName.trim().length() > 0) {
			sb.append("<p><b>LDAP auxliary obiectClass name: </b>"
					+ ldapAuxiliaryClassName + "</p>\n");
		}
		if (!isAddToSchema) {
			sb.append("<p><b>LDAP obiectClass is already present in core schema</b></p>\n");
		}
		if (desc != null && desc.trim().length() > 0) {
			sb.append("<p><b>Description: </b>").append(desc).append("</p>\n");
		}
		sb.append("<p>&nbsp;</p>\n");

		JDocComment doc = dClass.javadoc();
		doc.add(0, sb.toString());
	}

	public static String getLinkDoc(JType type) {
		return type.isPrimitive() ? type.name() : "{@link " + type.fullName() + "}";
	}

	public static JMethod getSetterMethod(JDefinedClass implClass, JFieldVar fieldVar) {
		String methodName = "set" + StringUtils.capitalize(fieldVar.name());
		JType[] types = new JType[] { fieldVar.type() };

		// Get method
		JMethod method = implClass.getMethod(methodName, types);
		if (method == null) {
			throw new RuntimeException("Not found setter method '" + methodName
					+ "' in '" + implClass + "'");
		}
		return method;
	}

	public static JMethod getGetterMethod(JDefinedClass implClass, JFieldVar fieldVar) {
		String methodName = "get" + StringUtils.capitalize(fieldVar.name());

		// Get method
		JMethod method = implClass.getMethod(methodName, null);
		if (method == null) {
			throw new RuntimeException("Not found getter method '" + methodName
					+ "' in '" + implClass + "'");
		}
		return method;
	}

	private void createToStringMethod(JDefinedClass implClass) {
		JCodeModel codeModel = implClass.owner();
		JMethod toStringMethod =
				implClass.method(JMod.PUBLIC, codeModel.ref(String.class),
						"toString");
		toStringMethod.annotate(Override.class);
		toStringMethod.body()._return(
				codeModel.ref(ToStringBuilder.class)
						.staticInvoke("reflectionToString").arg(JExpr._this()));

		toStringMethod =
				implClass.method(JMod.PUBLIC, codeModel.ref(String.class),
						"toString");
		JVar style = toStringMethod.param(ToStringStyle.class, "style");
		toStringMethod.body()._return(
				codeModel.ref(ToStringBuilder.class)
						.staticInvoke("reflectionToString").arg(JExpr._this())
						.arg(style));
	}

	private void createCloneMethod(JDefinedClass implClass) {
		JClass serializableJClass = implClass.owner().ref(Serializable.class);
		for (Iterator<JClass> iterator = implClass._implements(); iterator.hasNext();) {
			if (serializableJClass.isAssignableFrom(iterator.next())) {
				implClass._implements(Cloneable.class);

				JCodeModel codeModel = implClass.owner();
				JMethod cloneMethod =
						implClass.method(JMod.PUBLIC, Object.class, "clone");
				cloneMethod.annotate(Override.class);
				cloneMethod._throws(CloneNotSupportedException.class);
				cloneMethod.body()._return(
						codeModel.ref(SerializationUtils.class)
								.staticInvoke("clone").arg(JExpr._this()));
				return;
			}
		}
	}

	private void serializable(JDefinedClass implClass, String uid) {
		// Add implements Serializable
		implClass._implements(Serializable.class);

		// Add serialVersionUID
		implClass.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL,
				implClass.owner().LONG, "serialVersionUID", JExpr
						.lit((uid != null && uid.trim().length() > 0) ? Long
								.parseLong(uid) : new Random().nextLong()));
	}

	private void dataSerializableAsString(JDefinedClass implClass,
			String helperClass, String[] args, String pattern, String format) {
		List<JFieldVar> argFields = new ArrayList<JFieldVar>();
		if (args != null && args.length > 0) {
			for (String arg : args) {
				JFieldVar argField = implClass.fields().get(arg);
				// Add FormParam annotation
				JAnnotationUse annotationUse = argField.annotate(FormParam.class);
				annotationUse.param("value",
						StringUtils.uncapitalize(argField.name()));

				argFields.add(argField);
			}
		}

		JCodeModel codeModel = implClass.owner();

		// Add implements DataSerializableAsString
		implClass._implements(DataSerializableAsString.class);

		// Add method getDataSerializablePattern
		JMethod method =
				implClass.method(JMod.PUBLIC, codeModel.ref(Pattern.class),
						"getDataSerializablePattern");
		JFieldVar fieldPattern = null;
		if (pattern != null && pattern.trim().length() > 0) {
			// Add SerializablePattern
			fieldPattern =
					implClass.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL,
							Pattern.class, "SerializablePattern",
							codeModel.ref(Pattern.class).staticInvoke("compile")
									.arg(pattern));
			method.body()._return(fieldPattern);
		} else {
			method.body()._return(JExpr._null());
		}
		// Add JsonIgnore annotation
		method.annotate(JsonIgnore.class);

		JFieldVar fieldFormat = null;
		if (format != null && format.trim().length() > 0) {
			// Add MessageFormat format
			fieldFormat =
					implClass.field(
							JMod.PRIVATE | JMod.STATIC | JMod.FINAL,
							MessageFormat.class,
							"SerializableFormat",
							JExpr._new(codeModel.ref(MessageFormat.class)).arg(
									format));
		}

		// Add method read
		method = implClass.method(JMod.PUBLIC, codeModel.ref(String.class), "read");
		JInvocation invoke = codeModel.ref(helperClass).staticInvoke("read");
		invoke.arg(fieldFormat == null ? JExpr._null() : fieldFormat);
		for (JFieldVar argField : argFields) {
			invoke.arg(argField);
		}
		method.body()._return(invoke);

		// Add method write
		method = implClass.method(JMod.PUBLIC, codeModel.VOID, "write");
		JVar param = method.param(codeModel.ref(String.class), "value");
		JVar parts =
				method.body().decl(
						codeModel.ref(Object[].class),
						"parts",
						codeModel
								.ref(helperClass)
								.staticInvoke("write")
								.arg(param)
								.arg(fieldPattern == null ? JExpr._null()
										: fieldPattern));
		JConditional conditional = method.body()._if(parts.ne(JExpr._null()));
		JBlock ifBlock = conditional._then();
		for (int i = 0; i < argFields.size(); i++) {
			JCast jCast =
					new JCast(argFields.get(i).type(), parts.component(JExpr.lit(i)));
			ifBlock.assign(argFields.get(i), jCast);
		}
		JBlock elseBlock = conditional._else();
		for (int i = 0; i < argFields.size(); i++) {
			elseBlock.assign(argFields.get(i), JExpr._null());
		}
	}

	private void createGetBooleanMethod(JDefinedClass implClass) {
		for (JMethod method : implClass.methods()) {
			if (method.name().startsWith("is") && !method.name().startsWith("isSet")) {
				JMethod getMethod =
						implClass.method(
								JMod.PUBLIC,
								method.type(),
								(new StringBuilder("get")).append(
										method.name().substring(2)).toString());
				getMethod.body()
						.add((JStatement) method.body().getContents().get(0));
			}
		}
	}

	private void createFillDefaultValuesMethod(ClassOutline co) {

		Map<String, JFieldVar> fields = co.implClass.fields();
		JMethod method =
				co.implClass.method(JMod.PUBLIC, co.implClass.owner().VOID,
						"fillDefaultValues");
		method.javadoc().add("Fill object fields with yours default values");

		if (!co.implClass._extends().equals(co.implClass.owner().ref(Object.class))) {
			method.body().invoke(JExpr._super(), "fillDefaultValues");
		}

		JFieldVar dtf = null;
		for (FieldOutline fieldOutline : co.getDeclaredFields()) {

			CPropertyInfo fieldInfo = fieldOutline.getPropertyInfo();
			if (!(fieldInfo.getSchemaComponent() instanceof XSParticle)) {
				continue;
			}

			XSTerm term = ((XSParticle) fieldInfo.getSchemaComponent()).getTerm();
			if (!term.isElementDecl()) {
				continue;
			}

			XSElementDecl element = term.asElementDecl();

			// recupero il valore di default e fixed
			String defaultValue = null;
			if (element.getDefaultValue() != null) {
				defaultValue = element.getDefaultValue().value;
			}

			String fixedValue = null;
			if (element.getFixedValue() != null) {
				fixedValue = element.getFixedValue().value;
			}

			// recupero il parametro
			JFieldVar var = fields.get(fieldInfo.getName(false));
			JType type = fieldOutline.getRawType();
			if (type.isPrimitive()) {
				type = type.boxify();
			}

			// recupero il nome del parametro
			String typeFullName = type.fullName();

			if ("java.lang.String".equals(typeFullName)) {
				if (defaultValue != null) {
					method.body().assign(var, JExpr.lit(defaultValue));
				} else if (fixedValue != null) {
					var.init(JExpr.lit(fixedValue));
				}
			}

			else if ("java.lang.Boolean".equals(typeFullName)) {
				if (defaultValue != null) {
					method.body().assign(var,
							JExpr.lit(Boolean.valueOf(defaultValue).booleanValue()));
				} else if (fixedValue != null) {
					var.init(JExpr.lit(Boolean.valueOf(fixedValue).booleanValue()));
				}
			} else if ("java.lang.Byte".equals(typeFullName)
					|| "java.lang.Short".equals(typeFullName)
					|| "java.lang.Integer".equals(typeFullName)) {
				if (defaultValue != null) {
					method.body().assign(var,
							JExpr.lit(Integer.valueOf(defaultValue).intValue()));
				} else if (fixedValue != null) {
					var.init(JExpr.lit(Integer.valueOf(fixedValue).intValue()));
				}
			} else if ("java.lang.Long".equals(typeFullName)) {
				if (defaultValue != null) {
					method.body().assign(var,
							JExpr.lit(Long.valueOf(defaultValue).longValue()));
				} else if (fixedValue != null) {
					var.init(JExpr.lit(Long.valueOf(fixedValue).longValue()));
				}
			} else if ("java.lang.Float".equals(typeFullName)) {
				if (defaultValue != null) {
					method.body().assign(var,
							JExpr.lit(Float.valueOf(defaultValue).floatValue()));
				} else if (fixedValue != null) {
					var.init(JExpr.lit(Float.valueOf(fixedValue).floatValue()));
				}
			} else if ("java.lang.Single".equals(typeFullName)
					|| "java.lang.Double".equals(typeFullName)) {
				if (defaultValue != null) {
					method.body().assign(var,
							JExpr.lit(Double.valueOf(defaultValue).doubleValue()));
				} else if (fixedValue != null) {
					var.init(JExpr.lit(Double.valueOf(fixedValue).doubleValue()));
				}
			} else if ("javax.xml.datatype.XMLGregorianCalendar"
					.equals(typeFullName)) {
				if (dtf == null) {
					dtf = installDtF(co.implClass);
					if (dtf == null)
						continue;
				}
				if (defaultValue != null) {
					method.body().assign(
							var,
							JExpr.invoke(dtf, "newXMLGregorianCalendar").arg(
									defaultValue));
				} else if (fixedValue != null) {
					var.init(JExpr.invoke(dtf, "newXMLGregorianCalendar").arg(
							fixedValue));
				}
			} else if ((type instanceof JDefinedClass)
					&& ((JDefinedClass) type).getClassType() == ClassType.ENUM) {
				if (defaultValue != null) {
					JEnumConstant constant =
							findEnumConstant(type, defaultValue, co.parent());
					if (constant != null) {

						method.body().assign(var, constant);
					}
				} else if (fixedValue != null) {
					JEnumConstant constant =
							findEnumConstant(type, fixedValue, co.parent());
					if (constant != null) {
						var.init(constant);
					}
				}
			} else {
				continue;
			}
		}
	}

	private JEnumConstant findEnumConstant(JType enumType, String enumStringValue,
			Outline outline) {
		for (EnumOutline eo : outline.getEnums()) {

			if (eo.clazz == enumType) {
				for (EnumConstantOutline eco : eo.constants) {
					if (eco.target.getLexicalValue().equals(enumStringValue))
						return eco.constRef;
				}

				System.err.println((new StringBuilder())
						.append("[WARN] Could not find EnumConstant for value: ")
						.append(enumStringValue).toString());
				return null;
			}
		}

		System.err.println((new StringBuilder())
				.append("[WARN] Could not find Enum class for type: ")
				.append(enumType.fullName()).toString());
		return null;
	}

	private JFieldVar installDtF(JDefinedClass parentClass) {
		try {
			JCodeModel cm = parentClass.owner();
			JClass dtfClass = cm.ref(javax.xml.datatype.DatatypeFactory.class);
			JFieldVar dtf = parentClass.field(28, dtfClass, "DATATYPE_FACTORY");
			JBlock si = parentClass.init();
			JTryBlock tryBlock = si._try();
			tryBlock.body().assign(dtf, dtfClass.staticInvoke("newInstance"));
			JCatchBlock catchBlock =
					tryBlock._catch(cm
							.ref(javax.xml.datatype.DatatypeConfigurationException.class));
			com.sun.codemodel.JVar ex = catchBlock.param("ex");
			JClass runtimeException = cm.ref(java.lang.RuntimeException.class);
			catchBlock.body()._throw(
					JExpr._new(runtimeException)
							.arg("Unable to initialize DatatypeFactory").arg(ex));
			return dtf;
		}
		catch (Exception e) {
			System.err.println("[ERROR] Failed to create code");
			e.printStackTrace();
			return null;
		}
	}

	// Solo per debug
	public static void main(String[] args) throws Throwable {
		args =
				new String[] { "-extension", "-d", "../Schema/src.generated",
						"../Schema/schemas/nabookHD.xsd", "-Xldapper" };
		XJCFacade.main(args);
	}
}
