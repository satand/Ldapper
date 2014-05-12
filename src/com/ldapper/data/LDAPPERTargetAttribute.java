package com.ldapper.data;

import java.util.List;

public interface LDAPPERTargetAttribute {
	
	public static final LDAPPERTargetAttribute[] emptyLDAPPERTargetAttributes = new LDAPPERTargetAttribute[0];

	public String getName();
	
	public void setName(String name);
	
	public List<String> getValueRegexList();

	public void setValueRegexList(List<String> valueRegexList);
}
