package com.ldapper.context.control;

import java.io.IOException;

import com.sun.jndi.ldap.BasicControl;
import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerEncoder;

public class GetEffectiveRightsControl extends BasicControl
{

    private static final long serialVersionUID = 0x7a9282ec1c1fab5L;
    
	/**
	 * The OID for the get effective rights request control.
	 */
	public static final String OID_GET_EFFECTIVE_RIGTHS_REQUEST_CONTROL = "1.3.6.1.4.1.42.2.27.9.5.2";
	
    public GetEffectiveRightsControl(String authzId, String[] attributes)
        throws IOException
    {
        super(OID_GET_EFFECTIVE_RIGTHS_REQUEST_CONTROL, true, null);
        super.value = setEncodedValue(authzId, attributes);
    }

    private static byte[] setEncodedValue(String authzId, String[] attributes)
        throws IOException
    {
        BerEncoder berencoder = new BerEncoder(256);
        berencoder.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
        berencoder.encodeString(authzId, true);
        berencoder.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
        berencoder.encodeStringArray(attributes, true);
        berencoder.endSeq();
        berencoder.endSeq();
        return berencoder.getTrimmedBuf();
    }

}