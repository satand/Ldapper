package com.ldapper.context.search;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public class PagedResultsForFilterExpr<T extends LDAPPERBean> extends PagedResults<T> {

	private static final long serialVersionUID = -4845669775709784428L;

	private String filterExpr;
	private String[] filterArgs;
	private SearchControls searchControls;

	public PagedResultsForFilterExpr() {}

	public PagedResultsForFilterExpr(LdapContext ctx, LDAPPERBean startPointObj,
			Class<T> objClass, String filterExpr, String[] filterArgs,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, boolean returnObj, int contentsForPage,
			int startPageIndex, String... sortBy) throws Exception {
		this(ctx, startPointObj != null ? startPointObj.getDN() : "", objClass,
				filterExpr, filterArgs, scope, targetAttributes, targetNestedObjs,
				returnObj, contentsForPage, startPageIndex, sortBy);
	}

	public PagedResultsForFilterExpr(LdapContext ctx, String startPointDN,
			Class<T> objClass, String filterExpr, String[] filterArgs,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, boolean returnObj, int contentsForPage,
			int startPageIndex, String... sortBy) throws Exception {
		init(ctx, startPointDN, objClass, targetAttributes, targetNestedObjs,
				returnObj, contentsForPage, sortBy);

		this.filterExpr =
				ExternalObjTemplate.getInstance(objClass)
						.addObjClassesToSearchFilter(filterExpr);
		this.filterArgs = filterArgs;

		// Create SearchControls
		searchControls = new SearchControls();
		searchControls.setSearchScope(scope.value);
		searchControls.setReturningAttributes(attrsToReturn);
		searchControls.setReturningObjFlag(returningObj);

		// Get start page to calculate contentCount
		getPageResults(startPageIndex);
	}

	@Override
	NamingEnumeration<SearchResult> search(LdapContext ctx) throws NamingException {
		return ctx.search(startPointDN, filterExpr, filterArgs, searchControls);
	}

	protected void copyFromOtherInstance(PagedResults<?> pr) {
		super.copyFromOtherInstance(pr);

		PagedResultsForFilterExpr<?> prffe = (PagedResultsForFilterExpr<?>) pr;
		filterExpr = prffe.filterExpr;
		filterArgs = prffe.filterArgs;
		searchControls = prffe.searchControls;
	}
}
