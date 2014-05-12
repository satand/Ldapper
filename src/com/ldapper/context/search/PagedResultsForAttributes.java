package com.ldapper.context.search;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public class PagedResultsForAttributes<T extends LDAPPERBean> extends PagedResults<T> {

	private static final long serialVersionUID = 1024448138815427674L;

	private Attributes matchingAttrs;

	public PagedResultsForAttributes() {}

	public PagedResultsForAttributes(LdapContext ctx, LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, boolean returnObj, int contentsForPage,
			int startPageIndex, String... sortBy) throws Exception {
		this(ctx, startPointObj != null ? startPointObj.getDN() : "", obj,
				targetAttributes, targetNestedObjs, returnObj, contentsForPage,
				startPageIndex, sortBy);
	}

	public PagedResultsForAttributes(LdapContext ctx, String startPointDN, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returnObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception {
		init(ctx, startPointDN, obj.getClass(), targetAttributes, targetNestedObjs,
				false, contentsForPage, sortBy);

		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);
		// Get matching attributes
		matchingAttrs = eot.getAttributes(obj);

		// Get start page to calculate contentCount
		getPageResults(startPageIndex);
	}

	@Override
	NamingEnumeration<SearchResult> search(LdapContext ctx) throws NamingException {
		return ctx.search(startPointDN, matchingAttrs, attrsToReturn);
	}

	protected void copyFromOtherInstance(PagedResults<?> pr) {
		super.copyFromOtherInstance(pr);

		PagedResultsForAttributes<?> prfa = (PagedResultsForAttributes<?>) pr;
		matchingAttrs = prfa.matchingAttrs;
	}

}
