package com.ldapper.context.control;

import java.io.IOException;

import com.sun.jndi.ldap.BasicControl;
import com.sun.jndi.ldap.BerEncoder;

public class ProxyAuthorizationControl extends BasicControl
{

    private static final long serialVersionUID = 0x7a9282ec1c1fab5L;

	/**
	 * The OID for the proxy authorization request control.
	 */
	public static final String OID_PROXYAUTH_REQUEST_CONTROL = "2.16.840.1.113730.3.4.18";
	
	private String authIdDN;
	
    public ProxyAuthorizationControl(String authIdDN)
    	throws IOException
    {
    	this(authIdDN, null);
    }
    
	public ProxyAuthorizationControl(String authIdDN, String ctxName) 
		throws IOException
	{
    	super(OID_PROXYAUTH_REQUEST_CONTROL, true, null);
    	
    	this.authIdDN = authIdDN;
    	
    	String completeAuthId = "dn:" + authIdDN;
    	if (ctxName != null && ctxName.trim().length()>0) {
    		completeAuthId += "," + ctxName;
    	}
        super.value = setEncodedValue(completeAuthId);
	}

    private static byte[] setEncodedValue(String authzId)
        throws IOException
    {
        BerEncoder berencoder = new BerEncoder(2 * authzId.length() + 5);
        berencoder.encodeString(authzId, true);
        return berencoder.getTrimmedBuf();
    }

	public String getAuthIdDN() {
		return authIdDN;
	}

}