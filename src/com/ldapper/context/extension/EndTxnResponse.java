package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedResponse;

public class EndTxnResponse implements ExtendedResponse {

	private static final long serialVersionUID = 1231448220312153173L;
	
	private byte[] respEncondedValue;
	private boolean success;
	
	public EndTxnResponse(String id, byte[] berValue, int offset, int length) throws NamingException {
        respEncondedValue = berValue;
        success = berValue==null;

        //TODO Decode other data in case of failure
    }

    public byte[] getEncodedValue() {
        return respEncondedValue;
    }

    public boolean success() {
        return success;
    }
    
    public String getID() {
        return null;
    }
}
