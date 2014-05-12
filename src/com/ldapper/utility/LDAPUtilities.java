package com.ldapper.utility;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.ContextNotEmptyException;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortKey;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.SupportedObjType;
import com.ldapper.data.extension.EntryMapDataSerializableAsString;
import com.ldapper.exception.InvalidBaseDN;
import com.ldapper.exception.InvalidDN;
import com.sun.jndi.ldap.LdapURL;

public class LDAPUtilities {

	private static final String BASE_DN_PART_REGEX =
			"\\(\\[\\^\\=\\,\\\\\\+\\]\\+\\)";

	public static final String escapeLDAPSearchFilterIgnoreStar(String filter) {
		return escapeLDAPSearchFilter(filter, true);
	}

	public static final String escapeLDAPSearchFilter(String filter) {
		return escapeLDAPSearchFilter(filter, false);
	}

	private static final String escapeLDAPSearchFilter(String filter,
			boolean ignoreStar) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < filter.length(); i++) {
			char curChar = filter.charAt(i);
			switch (curChar) {
				case '\\':
					sb.append("\\5c");
					break;
				case '*':
					sb.append(ignoreStar ? curChar : "\\2a");
					break;
				case '(':
					sb.append("\\28");
					break;
				case ')':
					sb.append("\\29");
					break;
				case '\u0000':
					sb.append("\\00");
					break;
				default:
					sb.append(curChar);
			}
		}
		return sb.toString();
	}

	public static String escapeDN(String name) {
		StringBuilder sb = new StringBuilder();
		if (name.length() > 0 && (name.charAt(0) == ' ' || name.charAt(0) == '#')) {
			// add the leading backslash if needed
			sb.append('\\');
		}
		for (int i = 0; i < name.length(); i++) {
			char curChar = name.charAt(i);
			switch (curChar) {
				case '\\':
					sb.append("\\\\");
					break;
				case ',':
					sb.append("\\,");
					break;
				case '+':
					sb.append("\\+");
					break;
				case '"':
					sb.append("\\\"");
					break;
				case '<':
					sb.append("\\<");
					break;
				case '>':
					sb.append("\\>");
					break;
				case ';':
					sb.append("\\;");
					break;
				default:
					sb.append(curChar);
			}
		}
		if (name.length() > 1 && name.charAt(name.length() - 1) == ' ') {
			// add the trailing backslash if needed
			sb.insert(sb.length() - 1, '\\');
		}
		return sb.toString();
	}

	public static SortControl getSortControl(ExternalObjTemplate eot,
			String... sortBy) throws IOException {
		SortKey[] sortKeys;
		if (sortBy == null || sortBy.length == 0) {
			sortKeys = new SortKey[] { new SortKey(eot.getDNAttributeName()) };
		} else {
			sortKeys = new SortKey[sortBy.length];
			for (int i = 0; i < sortBy.length; i++) {
				if (sortBy[i].startsWith("-")) {
					sortKeys[i] = new SortKey(sortBy[i].substring(1), false, null);
				} else {
					sortKeys[i] = new SortKey(sortBy[i]);
				}
			}
		}

		return new SortControl(sortKeys, Control.CRITICAL);
	}

	public static boolean attributeContainsIgnoreCase(String value, Attribute attr)
			throws Exception {
		for (NamingEnumeration<?> e = attr.getAll(); e.hasMore();) {
			if (value.equalsIgnoreCase(e.next().toString())) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("rawtypes")
	public static Attribute mergeAttribute(Attribute attr1, Attribute attr2,
			SupportedObjType baseManagedObjType, Class<?> baseManagedObjClass)
			throws Exception {
		if (!attr1.getID().equals(attr2.getID())) {
			throw new Exception("Attribute IDs are not equals!");
		}

		Attribute attr = (Attribute) attr1.clone();

		// Get attr1 values
		ArrayList values1 = Collections.list(attr.getAll());

		// Remove attr1 values with duplicate key in respect to attr2 values
		Object key;
		Enumeration<?> valueAttr2Enum = attr2.getAll();
		while (valueAttr2Enum.hasMoreElements()) {
			key =
					((EntryMapDataSerializableAsString) SupportedObjType.convert(
							baseManagedObjType, baseManagedObjClass,
							valueAttr2Enum.nextElement())).getKey();
			for (Iterator iterator = values1.iterator(); iterator.hasNext();) {
				if (key.equals(((EntryMapDataSerializableAsString) SupportedObjType
						.convert(baseManagedObjType, baseManagedObjClass,
								iterator.next())).getKey())) {
					iterator.remove();
				}
			}
		}

		attr.clear();
		for (Object value1 : values1) {
			attr.add(value1);
		}

		valueAttr2Enum = attr2.getAll();
		while (valueAttr2Enum.hasMoreElements()) {
			attr.add(valueAttr2Enum.nextElement());
		}
		return attr;
	}

	public static Attribute mergeAttribute(Attribute attr1, Attribute attr2)
			throws Exception {
		if (!attr1.getID().equals(attr2.getID())) {
			throw new Exception("Attribute IDs are not equals!");
		}

		Attribute attr = (Attribute) attr1.clone();
		Enumeration<?> valueAttr2Enum = attr2.getAll();
		while (valueAttr2Enum.hasMoreElements()) {
			attr.add(valueAttr2Enum.nextElement());
		}
		return attr;
	}

	public static boolean compareAttribute(Attribute attr1, Attribute attr2)
			throws Exception {
		if (!attr1.getID().equals(attr2.getID())) {
			return false;
		}

		int size = attr1.size();
		if (attr1.size() != attr2.size()) {
			return false;
		}

		boolean order = attr1.isOrdered();
		if (order != attr2.isOrdered()) {
			return false;
		}

		if (order) {
			for (int i = 0; i < size; i++) {
				if (!attr1.get(i).equals(attr2.get(i))) {
					return false;
				}
			}
		} else {
			for (NamingEnumeration<?> e = attr1.getAll(); e.hasMore();) {
				if (!attr2.contains(e.next())) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean isCollection(Class<?> genericClass) {
		return Collection.class.isAssignableFrom(genericClass);
	}

	public static boolean isMap(Class<?> genericClass) {
		return Map.class.isAssignableFrom(genericClass);
	}

	public static void removeLDAPObjFromDN(LdapContext ctx, String dn)
			throws NamingException {
		try {
			ctx.unbind(dn);
			return;
		}
		catch (ContextNotEmptyException e) {
			// No supported tree delete
			removeLDAPObjFromDNTreeDeleteNotSupported(ctx, dn);
		}
	}

	public static void removeLDAPObjFromDNTreeDeleteNotSupported(LdapContext ctx,
			String dn) throws NamingException {
		NamingEnumeration<NameClassPair> e = ctx.list(dn);
		NameClassPair ncp;
		while (e.hasMoreElements()) {
			ncp = e.nextElement();
			removeLDAPObjFromDNTreeDeleteNotSupported(ctx, ncp.getNameInNamespace());
		}
		ctx.unbind(dn);
	}

	public static List<Control> getCtxRequestControls(LdapContext ctx)
			throws Exception {
		List<Control> reqControlsList = new ArrayList<Control>();
		Control[] reqControls = ctx.getRequestControls();
		if (reqControls != null) {
			for (Control control : reqControls) {
				reqControlsList.add(control);
			}
		}
		return reqControlsList;
	}

	public static Control getCtxRequestControl(LdapContext ctx, String controlOID)
			throws Exception {
		Control[] reqControls = ctx.getRequestControls();
		if (reqControls != null) {
			for (Control control : reqControls) {
				if (control.getID().equals(controlOID)) {
					return control;
				}
			}
		}
		return null;
	}

	public static void addRequestControlToContext(LdapContext ctx, Control control)
			throws Exception {
		try {
			List<Control> reqControls = LDAPUtilities.getCtxRequestControls(ctx);
			reqControls.add(control);
			ctx.setRequestControls(reqControls.toArray(new Control[reqControls
					.size()]));
		}
		catch (Exception e) {
			throw new Exception("Error adding '" + control.getClass().getName()
					+ "' to context request controls", e);
		}
	}

	public static void removeRequestControlFromContext(LdapContext ctx,
			Control control) throws Exception {
		try {
			List<Control> reqControls = LDAPUtilities.getCtxRequestControls(ctx);
			reqControls.remove(control);
			ctx.setRequestControls(reqControls.toArray(new Control[0]));
		}
		catch (Exception e) {
			throw new Exception("Error removing '" + control.getClass().getName()
					+ "' from context request controls", e);
		}
	}

	public static void removeRequestControlFromContext(LdapContext ctx,
			String controlOID) throws Exception {
		try {
			List<Control> reqControls = LDAPUtilities.getCtxRequestControls(ctx);
			for (Iterator<Control> iterator = reqControls.iterator(); iterator
					.hasNext();) {
				if (iterator.next().getID().equals(controlOID)) {
					iterator.remove();
					break;
				}
			}
			ctx.setRequestControls(reqControls.toArray(new Control[0]));
		}
		catch (Exception e) {
			throw new Exception("Error removing '" + controlOID
					+ "' control from context request controls", e);
		}
	}

	public static Attribute getSupportedExtensions(String username, String password,
			String ldapUrl, String auth) throws NamingException {
		InitialLdapContext ctx =
				getSimpleLdapContext(username, password, ldapUrl, auth);
		try {
			return ctx.getAttributes("", new String[] { "supportedExtension" }).get(
					"supportedExtension");
		}
		finally {
			ctx.close();
		}
	}

	public static Attribute getSupportedControls(String username, String password,
			String ldapUrl, String auth) throws NamingException {
		InitialLdapContext ctx =
				getSimpleLdapContext(username, password, ldapUrl, auth);
		try {
			return ctx.getAttributes("", new String[] { "supportedControl" }).get(
					"supportedControl");
		}
		finally {
			ctx.close();
		}
	}

	public static InitialLdapContext getSimpleLdapContext(String username,
			String password, String ldapUrl, String auth) throws NamingException {
		if (username == null) {
			throw new RuntimeException("Username is null!");
		}
		if (password == null) {
			throw new RuntimeException("Password is null!");
		}
		if (ldapUrl == null) {
			throw new RuntimeException("Provider LDAP URL is null!");
		}
		if (auth == null) {
			throw new RuntimeException("Authentication is null!");
		}

		Properties env = new Properties();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.SECURITY_PRINCIPAL, username);
		env.put(Context.SECURITY_CREDENTIALS, password);
		env.put(Context.PROVIDER_URL, ldapUrl);
		env.put(Context.SECURITY_AUTHENTICATION, auth);
		return new InitialLdapContext(env, null);
	}

	public static String getLDAPServerHost(String url) throws NamingException {
		return new LdapURL(url.split(" ")[0]).getHost();
	}

	public static int getLDAPServerPort(String url) throws NamingException {
		LdapURL ldapURL = new LdapURL(url.split(" ")[0]);
		int port = ldapURL.getPort();
		if (port == -1) {
			port = ldapURL.useSsl() ? 636 : 389;
		}
		return port;
	}

	public static String getDN(String attrIdName, Object attrIdValue, String baseDN)
			throws InvalidDN {
		// Validate DN attribute
		if (attrIdValue == null) {
			throw new InvalidDN("Object id attribute '" + attrIdName
					+ "' is not setted! (BaseDN=" + baseDN + ")");
		}
		String attrId = attrIdValue.toString();
		if (attrId == null || attrId.trim().length() == 0) {
			throw new InvalidDN("Object id attribute '" + attrIdName
					+ "' is invalid or empty! (BaseDN=" + baseDN + ")");
		}

		StringBuilder sb = new StringBuilder();
		sb.append(attrIdName).append('=').append(Rdn.escapeValue(attrId));
		if (baseDN != null && baseDN.trim().length() > 0) {
			sb.append(',').append(baseDN);
		}
		return sb.toString();
	}

	public static Object[] getDNParts(String dn, Class<?> attrIdClass,
			String attrIdName, Pattern baseDNPattern) throws InvalidDN {
		if (dn == null || dn.trim().length() == 0) {
			throw new InvalidDN("DN is null or empty!");
		}

		LdapName dnName;
		try {
			dnName = new LdapName(dn);
		}
		catch (Exception e) {
			throw new InvalidDN(dn, "DN '" + dn + "' is invalid:" + e.getMessage());
		}

		int idIndex = dnName.size() - 1;
		Rdn rdnId = dnName.getRdn(idIndex);
		// Check on DN attribute id
		if (rdnId.size() > 1 || !rdnId.getType().equals(attrIdName)) {
			throw new InvalidDN(dn, "DN '" + dn
					+ "' is invalid: It must begin with '" + attrIdName + "='");
		}

		String baseDN = dnName.getPrefix(idIndex).toString();
		if (baseDNPattern == null && !baseDN.isEmpty()) {
			throw new InvalidBaseDN(dn,
					"Base DN doesn't match with obiect's base DN pattern!");
		} else if (baseDNPattern != null) {
			// Check on baseDN
			Matcher m = baseDNPattern.matcher(baseDN);
			if (!m.matches()) {
				throw new InvalidBaseDN(baseDN, dn,
						"Base DN doesn't match with obiect's base DN pattern!");
			}
		}

		if (attrIdClass == String.class) {
			return new Object[] { rdnId.getValue(), baseDN };
		} else if (attrIdClass.isEnum()) {
			try {
				Method enumFromValue =
						attrIdClass.getMethod("fromValue", String.class);
				return new Object[] { enumFromValue.invoke(null, rdnId.getValue()),
						baseDN };
			}
			catch (Exception e) {
				throw new InvalidDN(dn, "Error converting a String object into a "
						+ attrIdClass.getName());
			}
		} else {
			throw new InvalidDN(dn,
					"I don't known to convert a String object into a "
							+ attrIdClass.getName());
		}
	}

	public static String getBaseDN(Pattern baseDNPattern, String... parts)
			throws InvalidBaseDN {
		if (parts == null || parts.length == 0) {
			throw new InvalidBaseDN("Parts array is null or empty");
		}

		String baseDN = baseDNPattern.pattern();
		for (String part : parts) {
			if (part == null || part.trim().length() == 0) {
				throw new InvalidBaseDN("A part is null or empty!");
			}
			baseDN = baseDN.replaceFirst(BASE_DN_PART_REGEX, part);
		}
		if (baseDN.contains("([^=,\\+]+)")) {
			throw new InvalidBaseDN("Insufficient parts!");
		}
		return baseDN;
	}

	public static String[] getBaseDNParts(Pattern baseDNPattern, String baseDN)
			throws InvalidBaseDN {
		if (baseDN == null || baseDN.trim().length() == 0) {
			throw new InvalidBaseDN("Base DN is null or empty!");
		}

		Matcher m = baseDNPattern.matcher(baseDN);
		if (!m.matches()) {
			throw new InvalidBaseDN(baseDN, null,
					"Base DN doesn't match with obiect's base DN pattern!");
		}
		String[] parts = new String[m.groupCount()];
		for (int i = 0; i < parts.length; i++) {
			parts[i] = m.group(i + 1);
		}
		return parts;
	}

	public static void main(String[] args) {
		try {
			Attribute attr =
					getSupportedExtensions("cn=Directory Manager", "password",
							"ldap://localhost:389", "simple");
			System.out.println("Supported Extensions=" + attr);

			attr =
					getSupportedControls("cn=Directory Manager", "password",
							"ldap://localhost:389", "simple");
			System.out.println("Supported Controls=" + attr);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
