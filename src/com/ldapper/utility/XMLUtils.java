package com.ldapper.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

public class XMLUtils {

	private static final Logger log = Logger.getLogger(XMLUtils.class);

	private static DocumentBuilder documentBuilder = null;
	private static Transformer transformer = null;
	private static ConcurrentHashMap<String, JAXBContext> contextMap =
			new ConcurrentHashMap<String, JAXBContext>();
	private static ConcurrentHashMap<String, Object> objectFactoryMap =
			new ConcurrentHashMap<String, Object>();
	private static ConcurrentHashMap<String, Method> objectFactoryMethodMap =
			new ConcurrentHashMap<String, Method>();
	private static ConcurrentHashMap<String, BeanValidator> beanValidatorMap =
			new ConcurrentHashMap<String, BeanValidator>();

	private XMLUtils() {}

	public static void setBeanValidator(Class<?> beanClass, BeanValidator bv) {
		String beanClassname = beanClass.getName();
		BeanValidator oldBeanValidator = beanValidatorMap.put(beanClassname, bv);
		if (log.isInfoEnabled()) {
			if (oldBeanValidator == null) {
				log.info("Using '" + bv.getClass().getName()
						+ "' to validate bean '" + beanClassname + "'");
			} else {
				log.info("Replacing '" + oldBeanValidator.getClass().getName()
						+ "' with '" + bv.getClass().getName()
						+ "' to validate bean '" + beanClassname + "'");
			}
		}
	}

	public static void validate(Object bean) throws Exception {
		BeanValidator beanValidator =
				beanValidatorMap.get(bean.getClass().getName());
		if (beanValidator != null) {
			beanValidator.validate(bean);
		} else {
			log.warn("No BeanValidator available for bean '"
					+ bean.getClass().getName()
					+ "'. Use setBeanValidator() before..");
		}
	}

	private static DocumentBuilder getDocumentBuilder()
			throws ParserConfigurationException {
		if (documentBuilder == null) {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

			documentBuilder = dbf.newDocumentBuilder();
		}

		return documentBuilder;
	}

	public static Transformer getTransformer()
			throws TransformerConfigurationException {
		if (transformer == null) {
			transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		}

		return transformer;
	}

	public static JAXBContext getContext(Class<?> bean) throws JAXBException {
		String pkgName = bean.getPackage().getName();

		JAXBContext result = contextMap.get(pkgName);
		if (result == null) {
			result = JAXBContext.newInstance(pkgName);
			contextMap.put(pkgName, result);
		}

		return result;
	}

	public static Marshaller createMarshaller(Class<?> bean, boolean formattedOutput)
			throws JAXBException {

		PrefixMapper prefixMapper = new PrefixMapper();

		JAXBContext jaxb = getContext(bean);
		Marshaller m = jaxb.createMarshaller();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, formattedOutput);
		m.setProperty("com.sun.xml.bind.namespacePrefixMapper", prefixMapper);

		// if (schema != null) {
		// m.setSchema(schema);
		// }

		return m;
	}

	public static Unmarshaller createUnmarshaller(Class<?> bean, Schema schema)
			throws JAXBException {
		JAXBContext jaxb = getContext(bean);
		Unmarshaller unmarshaller = jaxb.createUnmarshaller();

		// Per DEBUG: notifica gli errori
		// unmarshaller.setEventHandler(new DefaultValidationEventHandler());

		if (schema != null) {
			unmarshaller.setSchema(schema);
		}

		return unmarshaller;
	}

	public static Document toDocument(Object bean) {
		try {
			Document doc = getDocumentBuilder().newDocument();
			createMarshaller(bean.getClass(), true).marshal(infer(bean), doc);

			return doc;

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T fromDocument(Document doc, Class<T> bean, Schema schema) {
		try {
			return createUnmarshaller(bean, schema).unmarshal(doc, bean).getValue();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Document toDocumentForXPath(Object bean) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			createMarshaller(bean.getClass(), true).marshal(infer(bean), baos);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

			return getDocumentBuilder().parse(bais);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T fromDocumentForXPath(Document doc, Class<T> bean,
			Schema schema) {
		try {
			byte[] xmlData = getXmlBytes(doc);
			ByteArrayInputStream bais = new ByteArrayInputStream(xmlData);

			StreamSource source = new StreamSource(bais);

			return createUnmarshaller(bean, schema).unmarshal(source, bean)
					.getValue();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void printXml(Object bean, OutputStream stream) {
		try {
			createMarshaller(bean.getClass(), true).marshal(infer(bean), stream);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String toXml(Object bean) {
		return toXml(bean, true);
	}

	public static String toXml(Object bean, boolean formattedOutput) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			createMarshaller(bean.getClass(), formattedOutput).marshal(infer(bean),
					baos);

			return new String(baos.toByteArray());

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T fromXml(String xml, Class<T> bean, Schema schema) {
		return fromXml(new ByteArrayInputStream(xml.getBytes()), bean, schema);
	}

	public static <T> T fromXml(InputStream xmlStream, Class<T> bean, Schema schema) {
		try {
			XMLStreamReader xmlStreamReader =
					XMLInputFactory.newInstance().createXMLStreamReader(xmlStream);
			return createUnmarshaller(bean, schema).unmarshal(xmlStreamReader, bean)
					.getValue();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void transform(Object bean, Source stylesheetSource,
			OutputStream stream) {
		try {
			TransformerHandler handler =
					((SAXTransformerFactory) TransformerFactory.newInstance())
							.newTransformerHandler(stylesheetSource);

			handler.setResult(new StreamResult(stream));

			createMarshaller(bean.getClass(), true).marshal(infer(bean), handler);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static int getFragmentLen(Object bean) {
		try {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();

			Marshaller marshaller = createMarshaller(bean.getClass(), true);
			marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);

			marshaller.marshal(infer(bean), stream);

			int len = stream.size();
			stream.close();

			return len;

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static byte[] getXmlBytes(Document doc) {
		try {
			Transformer transformer = getTransformer();

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			StreamResult result = new StreamResult(baos);

			DOMSource source = new DOMSource(doc);
			transformer.transform(source, result);

			return baos.toByteArray();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Object infer(Object bean) throws Exception {
		Class<?> clazz = bean.getClass();
		XmlRootElement xmlRootElement = clazz.getAnnotation(XmlRootElement.class);
		if (xmlRootElement == null) {
			String pack = clazz.getPackage().getName();

			Object objFac = objectFactoryMap.get(pack);
			if (objFac == null) {
				objFac = Class.forName(pack + ".ObjectFactory").newInstance();
				objectFactoryMap.put(pack, objFac);

				Method[] methods = objFac.getClass().getMethods();
				for (Method method : methods) {
					Class<?>[] paramsClass = method.getParameterTypes();
					if (paramsClass.length == 1) {
						objectFactoryMethodMap.put(paramsClass[0].getName(), method);
					}
				}
			}

			Method method = objectFactoryMethodMap.get(clazz.getName());
			if (method != null) {
				return method.invoke(objFac, bean);
			} else {
				return null;
			}

		} else {
			return bean;
		}
	}

	private static class PrefixMapper extends NamespacePrefixMapper {

		private static HashMap<String, String> nsMap = new HashMap<String, String>();
		private static int nsIndex = 1;

		@Override
		public String getPreferredPrefix(String namespaceUri, String suggestion,
				boolean requirePrefix) {

			if (namespaceUri.equals("SYNCML:SYNCML1.1")) {
				return "n1";
			} else if (namespaceUri.equals("syncml:metinf")) {
				return "n2";
			} else if (namespaceUri.equals("syncml:devinf")) {
				return "n3";
			}

			String prefix = nsMap.get(namespaceUri);
			if (prefix == null) {
				prefix = "n" + nsIndex++;
				nsMap.put(namespaceUri, prefix);
			}

			return prefix;
		}
	}

}
