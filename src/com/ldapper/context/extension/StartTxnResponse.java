package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedResponse;

public class StartTxnResponse implements ExtendedResponse {

	private static final long serialVersionUID = -6944468205158256688L;
	
	private byte[] respEncondedValue;
	private String txnID;
	
	public StartTxnResponse(String id, byte[] berValue, int offset, int length) throws NamingException {
		if (berValue == null) {
			throw new NamingException("Receiced empty StartTxnResponse");
		}

		respEncondedValue = berValue;
		txnID = new String(respEncondedValue);
    }
    
    public String getTxnID() {
    	return txnID;
    }

    public byte[] getEncodedValue() {
        return respEncondedValue;
    }
    
    public String getID() {
        return null;
    }
}
