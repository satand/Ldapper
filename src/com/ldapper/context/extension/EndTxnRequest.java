package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;

import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerEncoder;

public class EndTxnRequest implements ExtendedRequest {

	private static final long serialVersionUID = -8842281505506972033L;

	/**
	 * The request OID for the end transaction extended operation.
	 */
	public static final String OID_END_TRANSACTION_REQUEST = "2.16.840.1.113730.3.5.3";

	private byte[] encodedValue;
	
	public EndTxnRequest(boolean commit, String txnID) throws Exception {
		setEncodedValue(commit, txnID);
	}
	
	// Methods used by service providers
	public String getID() {
		return OID_END_TRANSACTION_REQUEST;
	}

	private void setEncodedValue(boolean commit, String txnID) throws Exception {
		if (txnID==null || txnID.trim().length()==0) {
			throw new Exception("Transaction ID is null or empty!");
		}

		// build the ASN.1 BER encoding
		BerEncoder ber = new BerEncoder();
		ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
		ber.encodeBoolean(commit);
		ber.encodeOctetString(txnID.getBytes(), Ber.ASN_SIMPLE_STRING); //equivalent code: ber.encodeString(txnID, true);
		ber.endSeq();

		encodedValue = ber.getTrimmedBuf();
	}
	
	public byte[] getEncodedValue() {
		return encodedValue;
	}

	public ExtendedResponse createExtendedResponse(
			String id, byte[] berValue,	int offset, int length) throws NamingException
	{
		return new EndTxnResponse(id, berValue, offset, length);
	}

}
