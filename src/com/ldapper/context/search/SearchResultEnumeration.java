package com.ldapper.context.search;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.log4j.Logger;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.context.template.NestedObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;
import com.ldapper.exception.InvalidDN;
import com.ldapper.exception.ReadLDAPObjectException;
import com.ldapper.utility.LDAPUtilities;

public class SearchResultEnumeration implements Enumeration<LDAPPERBean> {

	private static final Logger log = Logger
			.getLogger(SearchResultEnumeration.class);

	private LdapContext ctx;
	private NamingEnumeration<SearchResult> namingEnumeration;
	private ExternalObjTemplate resultEot;
	private LDAPPERTargetAttribute[] targetAttributes;
	private List<NestedObjTemplate> nestedObjsToReturn;
	private int ctxDNNameLen;
	private boolean objIsInSearchResult;

	SearchResultEnumeration(LdapContext context,
			NamingEnumeration<SearchResult> namingEnumeration,
			Class<? extends LDAPPERBean> resultClass,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean objIsInSearchResult) throws Exception {
		resultEot = ExternalObjTemplate.getInstance(resultClass);
		if (resultEot.isAbstract() && resultEot.hasEmptyExternalObjTemplateChilds()) {
			throw new Exception("Not found childs for abstract class '"
					+ resultClass.getName() + "'");
		}
		this.nestedObjsToReturn = resultEot.checkTargetNestedObjs(targetNestedObjs);
		this.ctx = context.newInstance(context.getRequestControls());
		this.ctxDNNameLen = context.getNameInNamespace().length();
		this.namingEnumeration = namingEnumeration;
		this.targetAttributes = targetAttributes;
		this.objIsInSearchResult = objIsInSearchResult;
	}

	@Override
	public boolean hasMoreElements() {
		if (namingEnumeration == null) {
			return false;
		}

		try {
			return namingEnumeration.hasMore();
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
	}

	public LDAPPERBean nextElement(LDAPPERBean obj) {
		if (namingEnumeration == null) {
			return null;
		}

		try {
			return populateObjFromSearchResult(resultEot, obj,
					namingEnumeration.next());
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new RuntimeException("Error getting next object: "
					+ e.getMessage(), e);
		}
	}

	@Override
	public LDAPPERBean nextElement() {
		if (namingEnumeration == null) {
			return null;
		}

		try {
			SearchResult sr = namingEnumeration.next();
			if (resultEot.isAbstract()) {
				Attribute objectclassAttr = sr.getAttributes().get("objectclass");
				Map<String, ExternalObjTemplate> childEots =
						resultEot.getExternalObjTemplateChilds();
				ExternalObjTemplate eot = null;
				for (String key : childEots.keySet()) {
					if (objectclassAttr.contains(key)) {
						eot = childEots.get(key);
						break;
					}
				}
				if (eot == null) {
					throw new Exception(
							"Not found an LDAPPERBean for 'objectclass' attibute: "
									+ objectclassAttr);
				}

				return populateObjFromSearchResult(eot,
						eot.getExternalObjNewInstance(), sr);
			} else {
				return populateObjFromSearchResult(resultEot,
						resultEot.getExternalObjNewInstance(), sr);
			}
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new RuntimeException("Error getting next object: "
					+ e.getMessage(), e);
		}
	}

	public void close() {
		try {
			namingEnumeration.close();
		}
		catch (NamingException e) {
			log.error(e.getMessage(), e);
		}

		try {
			ctx.close();
		}
		catch (NamingException e) {
			log.error(e.getMessage(), e);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		close();
	}

	public ArrayList<LDAPPERBean> getAllSearchResults() {
		ArrayList<LDAPPERBean> res = new ArrayList<LDAPPERBean>();
		while (hasMoreElements()) {
			try {
				res.add(nextElement());
			}
			catch (RuntimeException e) {
				Throwable t = e.getCause();
				if (t == null || !InvalidDN.class.isAssignableFrom(t.getClass())) {
					throw e;
				}
			}
		}
		return res;
	}

	private LDAPPERBean populateObjFromSearchResult(ExternalObjTemplate eot,
			LDAPPERBean result, SearchResult sr) throws Exception {
		// Set DN on result
		String nameInSpace = sr.getNameInNamespace();
		String newObjDN =
				nameInSpace.substring(0, nameInSpace.length() - ctxDNNameLen);
		try {
			result.setDN(newObjDN);
		}
		catch (InvalidDN e) {
			String dn = e.getDN();
			if (dn != null) {
				log.error("Remove invalid object '" + dn + "'");
				LDAPUtilities.removeLDAPObjFromDN(ctx, dn);
			}
			throw e;
		}

		// Set search result's attributes on result
		eot.setAttributes(result, sr.getAttributes(), targetAttributes, false);
		// Set search result's object on result
		if (eot.getObjTemplate() != null) {
			try {
				if (objIsInSearchResult) {
					eot.setObject(result, sr.getObject());
				} else {
					eot.setObject(result, ctx.lookup(newObjDN));
				}
			}
			catch (Exception e) {
				throw new ReadLDAPObjectException(result, "Error setting object: "
						+ e.getMessage(), e);
			}
		}

		if (!nestedObjsToReturn.isEmpty()) {
			// Populate nested objects with all attributes on result
			SearchResultEnumeration sre = null;
			ArrayList<LDAPPERBean> nestedObjs;
			for (NestedObjTemplate not : nestedObjsToReturn) {
				try {
					sre =
							searchEnumeration(ctx, newObjDN,
									not.getBaseManagedObjClass(), null,
									SearchScope.ONELEVEL_SCOPE, null, null);
					nestedObjs = sre.getAllSearchResults();
					not.setNestedObj(result, nestedObjs);
				}
				finally {
					if (sre != null) {
						sre.close();
					}
				}
			}
		}
		return result;
	}

	// Search enumeration
	public static <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LdapContext ctx, String startPointDN, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);
		// Get matching attributes
		Attributes matchingAttrs = eot.getAttributes(obj);
		// Get attributes to return
		ArrayList<String> attrsToReturn =
				eot.checkTargetAttributes(targetAttributes);

		// Search
		NamingEnumeration<SearchResult> searchRes =
				ctx.search(startPointDN, matchingAttrs, attrsToReturn == null ? null
						: attrsToReturn.toArray(new String[attrsToReturn.size()]));
		return new SearchResultEnumeration(ctx, searchRes, obj.getClass(),
				targetAttributes, targetNestedObjs, false);
	}

	// Search enumeration
	public static <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LdapContext ctx, String startPointDN, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(objClass);
		// Get attributes to return
		ArrayList<String> attrsToReturn =
				eot.checkTargetAttributes(targetAttributes);
		// Create SearchControls
		SearchControls searchControls = new SearchControls();
		searchControls.setSearchScope(scope.value);
		searchControls.setReturningAttributes(attrsToReturn == null ? null
				: attrsToReturn.toArray(new String[attrsToReturn.size()]));
		searchControls.setReturningObjFlag(eot.getObjTemplate() != null);// TODO
																			// rendere
																			// settabile
																			// il
																			// flag

		// Search
		NamingEnumeration<SearchResult> searchRes =
				ctx.search(startPointDN, eot.addObjClassesToSearchFilter(filter),
						searchControls);
		return new SearchResultEnumeration(ctx, searchRes, objClass,
				targetAttributes, targetNestedObjs, true);
	}

	// Search enumeration
	public static <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LdapContext ctx, String startPointDN, Class<T> objClass,
			String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(objClass);
		// Get attributes to return
		ArrayList<String> attrsToReturn =
				eot.checkTargetAttributes(targetAttributes);
		// Create SearchControls
		SearchControls searchControls = new SearchControls();
		searchControls.setSearchScope(scope.value);
		searchControls.setReturningAttributes(attrsToReturn == null ? null
				: attrsToReturn.toArray(new String[attrsToReturn.size()]));
		searchControls.setReturningObjFlag(eot.getObjTemplate() != null);// TODO
																			// rendere
																			// settabile
																			// il
																			// flag

		// Search
		NamingEnumeration<SearchResult> searchRes =
				ctx.search(startPointDN,
						eot.addObjClassesToSearchFilter(filterExpr), filterArgs,
						searchControls);
		return new SearchResultEnumeration(ctx, searchRes, objClass,
				targetAttributes, targetNestedObjs, true);
	}
}
