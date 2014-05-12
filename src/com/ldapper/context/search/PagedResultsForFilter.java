package com.ldapper.context.search;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public class PagedResultsForFilter<T extends LDAPPERBean> extends PagedResults<T> {

	private static final long serialVersionUID = 7199219740442075848L;

	private String filter;
	private SearchControls searchControls;

	public PagedResultsForFilter() {}

	public PagedResultsForFilter(LdapContext ctx, LDAPPERBean startPointObj,
			Class<T> objClass, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returnObj, int contentsForPage, int startPageIndex,
			String... sortBy) throws Exception {
		this(ctx, startPointObj != null ? startPointObj.getDN() : "", objClass,
				filter, scope, targetAttributes, targetNestedObjs, returnObj,
				contentsForPage, startPageIndex, sortBy);
	}

	public PagedResultsForFilter(LdapContext ctx, String startPointDN,
			Class<T> objClass, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returnObj, int contentsForPage, int startPageIndex,
			String... sortBy) throws Exception {
		init(ctx, startPointDN, objClass, targetAttributes, targetNestedObjs,
				returnObj, contentsForPage, sortBy);

		this.filter =
				ExternalObjTemplate.getInstance(objClass)
						.addObjClassesToSearchFilter(filter);

		// Create SearchControls
		searchControls = new SearchControls();
		searchControls.setSearchScope(scope.value);
		searchControls.setReturningAttributes(attrsToReturn);
		searchControls.setReturningObjFlag(returningObj);

		// Get start page to calculate contentCount
		searchPageResults(startPageIndex);
	}

	@Override
	NamingEnumeration<SearchResult> search(LdapContext ctx) throws NamingException {
		return ctx.search(startPointDN, filter, searchControls);
	}

	protected void copyFromOtherInstance(PagedResults<?> pr) {
		super.copyFromOtherInstance(pr);

		PagedResultsForFilter<?> prff = (PagedResultsForFilter<?>) pr;
		filter = prff.filter;
		searchControls = prff.searchControls;
	}

}
