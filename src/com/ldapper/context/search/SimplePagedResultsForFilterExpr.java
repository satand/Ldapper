package com.ldapper.context.search;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public class SimplePagedResultsForFilterExpr<T extends LDAPPERBean> extends SimplePagedResults<T> {

	private static final long serialVersionUID = 4071528578509867527L;

	private String filterExpr;
	private String[] filterArgs;
	private SearchControls searchControls;

	public SimplePagedResultsForFilterExpr(LdapContext ctx,
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception {
		init(ctx, startPointObj, objClass, targetAttributes, targetNestedObjs,
				contentsForPage, sortBy);

		this.filterExpr =
				ExternalObjTemplate.getInstance(objClass)
						.addObjClassesToSearchFilter(filterExpr);
		this.filterArgs = filterArgs;

		// Create SearchControls
		searchControls = new SearchControls();
		searchControls.setSearchScope(scope.value);
		searchControls.setReturningAttributes(attrsToReturn);
		searchControls.setReturningObjFlag(ExternalObjTemplate.getInstance(objClass)
				.getObjTemplate() != null);// TODO rendere settabile il flag

		// Get first page to calculate contentCount
		initializeContentsCount();
	}

	@Override
	NamingEnumeration<SearchResult> search() throws NamingException {
		return ctx.search(startPointDN, filterExpr, filterArgs, searchControls);
	}

	@Override
	boolean objIsInSearchResult() {
		return true;
	}

	protected void copyFromOtherInstance(SimplePagedResults<?> spr) {
		super.copyFromOtherInstance(spr);

		SimplePagedResultsForFilterExpr<?> sprffe =
				(SimplePagedResultsForFilterExpr<?>) spr;
		filterExpr = sprffe.filterExpr;
		filterArgs = sprffe.filterArgs;
		searchControls = sprffe.searchControls;
	}
}
