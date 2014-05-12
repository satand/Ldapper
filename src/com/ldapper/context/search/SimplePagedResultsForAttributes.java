package com.ldapper.context.search;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public class SimplePagedResultsForAttributes<T extends LDAPPERBean> extends SimplePagedResults<T> {

	private static final long serialVersionUID = -2833537570922871103L;

	private Attributes matchingAttrs;

	public SimplePagedResultsForAttributes(LdapContext ctx,
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception {
		init(ctx, startPointObj, obj.getClass(), targetAttributes, targetNestedObjs,
				contentsForPage, sortBy);

		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);
		// Get matching attributes
		matchingAttrs = eot.getAttributes(obj);

		// Get first page to calculate contentCount
		initializeContentsCount();
	}

	@Override
	NamingEnumeration<SearchResult> search() throws NamingException {
		return ctx.search(startPointDN, matchingAttrs, attrsToReturn);
	}

	@Override
	boolean objIsInSearchResult() {
		return false;
	}

	protected void copyFromOtherInstance(SimplePagedResults<?> spr) {
		super.copyFromOtherInstance(spr);

		SimplePagedResultsForAttributes<?> sprfa =
				(SimplePagedResultsForAttributes<?>) spr;
		matchingAttrs = sprfa.matchingAttrs;
	}

}
