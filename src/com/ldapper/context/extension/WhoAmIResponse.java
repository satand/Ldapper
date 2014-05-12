package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedResponse;

public class WhoAmIResponse implements ExtendedResponse {

	private static final long serialVersionUID = -7844617917656212451L;

	private byte[] respEncondedValue;
	
	WhoAmIResponse(String id, byte[] berValue, int offset, int length) throws NamingException {
        respEncondedValue = berValue;
    }
    
    public String getRole() {
    	return new String(respEncondedValue);
    }

    public byte[] getEncodedValue() {
        return respEncondedValue;
    }
    
    public String getID() {
        return null;
    }
}
