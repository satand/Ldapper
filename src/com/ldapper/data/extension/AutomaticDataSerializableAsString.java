package com.ldapper.data.extension;

import com.ldapper.data.LDAPPERBean;


public interface AutomaticDataSerializableAsString extends DataSerializableAsString {

    /**
     * Set attribute automatic value. Parameter is the LDAPPERBean containing this attribute 
     */
	public void createAutomaticValue(LDAPPERBean obj);
}

