package com.ldapper.data;

import com.ldapper.utility.LDAPUtilities;

public enum ObjType {
	SIMPLE,
	ARRAY,
	COLLECTION,
	MAP;
	
	public static ObjType getObjType(Class<?> objClass) {
		if (objClass.isArray() && objClass.getComponentType()!=byte.class) {
			return ARRAY;
		}
		else if (LDAPUtilities.isCollection(objClass)) {
			return COLLECTION;
		}
		else if (LDAPUtilities.isMap(objClass)) {
			return MAP;
		}
		else {
			return SIMPLE;
		}
	}

}
