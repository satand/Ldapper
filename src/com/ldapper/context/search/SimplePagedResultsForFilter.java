package com.ldapper.context.search;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public class SimplePagedResultsForFilter<T extends LDAPPERBean> extends SimplePagedResults<T> {

	private static final long serialVersionUID = 634142019657531816L;

	private String filter;
	private SearchControls searchControls;

	public SimplePagedResultsForFilter(LdapContext ctx, LDAPPERBean startPointObj,
			Class<T> objClass, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception {
		init(ctx, startPointObj, objClass, targetAttributes, targetNestedObjs,
				contentsForPage, sortBy);

		this.filter =
				ExternalObjTemplate.getInstance(objClass)
						.addObjClassesToSearchFilter(filter);

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
		return ctx.search(startPointDN, filter, searchControls);
	}

	@Override
	boolean objIsInSearchResult() {
		return true;
	}

	protected void copyFromOtherInstance(SimplePagedResults<?> spr) {
		super.copyFromOtherInstance(spr);

		SimplePagedResultsForFilter<?> sprff = (SimplePagedResultsForFilter<?>) spr;
		filter = sprff.filter;
		searchControls = sprff.searchControls;
	}
}
