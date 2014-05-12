package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedResponse;

import com.sun.jndi.ldap.BerDecoder;

public class ModifyPasswordResponse implements ExtendedResponse {

	private static final long serialVersionUID = -7844617917656212451L;

	private byte[] respEncondedValue;
	private int offset;
	private int length;
	private String id;
	
	ModifyPasswordResponse(String identifier, byte[] berValue, 
			int off, int len) throws NamingException {
        respEncondedValue = berValue;
        offset = off;
        length = len;
        id = identifier;
    }
    
	  public static final byte TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD = (byte) 128;
	  
    public String getGenPassword() throws Exception {
    	if (respEncondedValue==null) {
    		return null;
    	}
    	
    	BerDecoder ber = new BerDecoder(respEncondedValue, offset, length);
    	ber.parseSeq(null);
    	return ber.parseStringWithTag(128, true, null);
    }

    public byte[] getEncodedValue() {
        return respEncondedValue;
    }
    
    public String getID() {
        return id;
    }
}
