package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;

import org.apache.log4j.Logger;

import com.ldapper.context.LDAPPERContext;

public class StartTxnRequest implements ExtendedRequest {

	private static final long serialVersionUID = -977221031225870555L;
	private static final Logger log = Logger.getLogger(StartTxnRequest.class);

	/**
	 * The request OID for the start transaction extended operation.
	 */
	public static final String OID_START_TRANSACTION_REQUEST =
			"2.16.840.1.113730.3.5.1";

	public String getID() {
		return OID_START_TRANSACTION_REQUEST;
	}

	public byte[] getEncodedValue() {
		return null;
	}

	public ExtendedResponse createExtendedResponse(String id, byte[] berValue,
			int offset, int length) throws NamingException {
		return new StartTxnResponse(id, berValue, offset, length);
	}

	public static void main(String[] args) {

		// opends
		LDAPPERContext ldapper =
				LDAPPERContext.createLDAPContext("cn=Directory Manager", "password",
						"ldap://localhost:1389/dc=example,dc=com");

		// Modify password
		try {
			StartTxnResponse response =
					(StartTxnResponse) ldapper
							.extendedOperation(new StartTxnRequest());
			System.out.println("Started new transaction with id: '"
					+ response.getTxnID() + "'");

			ldapper.extendedOperation(new EndTxnRequest(false, response.getTxnID()));
			System.out.println("Ended transaction with id: '" + response.getTxnID()
					+ "'");
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

}
