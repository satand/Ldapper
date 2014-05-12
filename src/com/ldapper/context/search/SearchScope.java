package com.ldapper.context.search;

import javax.naming.directory.SearchControls;

public enum SearchScope {

	OBJECT_SCOPE(SearchControls.OBJECT_SCOPE),
	ONELEVEL_SCOPE(SearchControls.ONELEVEL_SCOPE),
	SUBTREE_SCOPE(SearchControls.SUBTREE_SCOPE);
	
	int value;
	
	private SearchScope(int value) {
		this.value = value;
	}
	
	public int getScope() {
		return value;
	}
	
}
