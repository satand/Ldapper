package com.ldapper.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TargetAttribute implements LDAPPERTargetAttribute, Serializable {

	private static final long serialVersionUID = -9031363628205730879L;

	private String name;
	protected List<String> valueRegexList;
	
	public TargetAttribute(String attrName) {
		name = attrName;
	}
	
	public TargetAttribute(String attrName, List<String> attrValueRegexList) {
		name = attrName;
		valueRegexList = attrValueRegexList;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<String> getValueRegexList() {
		return valueRegexList;
	}
	
	public List<String> getValueRegexList(boolean create) {
		if (valueRegexList == null && create) {
			valueRegexList = new ArrayList<String>();
		}
		return valueRegexList;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setValueRegexList(List<String> valueRegexList) {
		this.valueRegexList = valueRegexList;
	}

	public static TargetAttribute[] getTargetAttributes(String[] targetAttrNames) {
		if (targetAttrNames == null) {
			return null;
		}

		TargetAttribute[] res = new TargetAttribute[targetAttrNames.length];
		for (int i = 0; i < targetAttrNames.length; i++) {
			res[i] = new TargetAttribute(targetAttrNames[i]);
		}
		return res;
	}
}
