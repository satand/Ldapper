package com.ldapper.context.extension;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;

import org.apache.log4j.Logger;

import com.ldapper.context.LDAPPERContext;

public class WhoAmIRequest implements ExtendedRequest {

	private static final Logger log = Logger.getLogger(WhoAmIRequest.class);

	private static final long serialVersionUID = 4854462720670336038L;

	public static final String OID_WHO_AM_I = "1.3.6.1.4.1.4203.1.11.3";

	// Methods used by service providers
	public String getID() {
		return OID_WHO_AM_I;
	}

	public byte[] getEncodedValue() {
		return null; // No value is needed for the WhoAmI request
	}

	public ExtendedResponse createExtendedResponse(String id, byte[] berValue,
			int offset, int length) throws NamingException {
		return new WhoAmIResponse(id, berValue, offset, length);
	}

	public static void main(String[] args) {
		// opends
		LDAPPERContext ldapper =
				LDAPPERContext.createLDAPContext("cn=Directory Manager", "password",
						"ldap://localhost:1389/dc=example,dc=com");

		// Who am I?
		try {
			WhoAmIResponse response =
					(WhoAmIResponse) ldapper.extendedOperation(new WhoAmIRequest());
			System.out.println("Role: " + response.getRole());
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

}
