package com.ldapper.context.extension;

import java.io.IOException;

import javax.naming.NamingException;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;

import org.apache.log4j.Logger;

import com.ldapper.context.LDAPPERContext;
import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerEncoder;

public class ModifyPasswordRequest implements ExtendedRequest {

	private static final long serialVersionUID = -162455216920322834L;

	/**
	 * The request OID for the password modify extended operation.
	 */
	public static final String OID_PASSWORD_MODIFY_REQUEST =
			"1.3.6.1.4.1.4203.1.11.1";

	/**
	 * The ASN.1 element type that will be used to encode the userIdentity
	 * component in a password modify extended request.
	 */
	public static final byte TYPE_PASSWORD_MODIFY_USER_ID = (byte) 0x80;

	/**
	 * The ASN.1 element type that will be used to encode the oldPasswd
	 * component in a password modify extended request.
	 */
	public static final byte TYPE_PASSWORD_MODIFY_OLD_PASSWORD = (byte) 0x81;

	/**
	 * The ASN.1 element type that will be used to encode the newPasswd
	 * component in a password modify extended request.
	 */
	public static final byte TYPE_PASSWORD_MODIFY_NEW_PASSWORD = (byte) 0x82;

	private static final Logger log = Logger.getLogger(ModifyPasswordRequest.class);

	private byte[] encodedValue;

	public ModifyPasswordRequest(String userIdentity, String oldPassword,
			String newPassword) throws IOException {
		setEncodedValue(userIdentity, oldPassword, newPassword);
	}

	// Methods used by service providers
	public String getID() {
		return OID_PASSWORD_MODIFY_REQUEST;
	}

	private void setEncodedValue(String userIdentity, String oldPassword,
			String newPassword) throws IOException {
		if (userIdentity == null && oldPassword == null && newPassword == null) {
			return;
		}

		// build the ASN.1 BER encoding
		BerEncoder ber = new BerEncoder();
		ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
		if (userIdentity != null) {
			ber.encodeString(userIdentity, TYPE_PASSWORD_MODIFY_USER_ID, true);
		}
		if (oldPassword != null) {
			ber.encodeString(oldPassword, TYPE_PASSWORD_MODIFY_OLD_PASSWORD, true);
		}
		if (newPassword != null) {
			ber.encodeString(newPassword, TYPE_PASSWORD_MODIFY_NEW_PASSWORD, true);
		}
		ber.endSeq();

		encodedValue = ber.getTrimmedBuf();
	}

	public byte[] getEncodedValue() {
		return encodedValue;
	}

	public ExtendedResponse createExtendedResponse(String id, byte[] berValue,
			int offset, int length) throws NamingException {
		return new ModifyPasswordResponse(id, berValue, offset, length);
	}

	public static void main(String[] args) {

		// opends
		LDAPPERContext ldapper =
				LDAPPERContext.createLDAPContext("cn=Manager", "secret",
						"ldap://localhost:1389/");

		// Modify password
		try {
			ModifyPasswordResponse response =
					(ModifyPasswordResponse) ldapper
							.extendedOperation(new ModifyPasswordRequest(
									"cn=Cino.Benedetti,ou=Users,cn=corp.com,dc=cab",
									null, null));
			if (response.getGenPassword() != null) {
				System.out.println("Generated password: "
						+ response.getGenPassword());
			} else {
				System.out.println("Change password success!");
			}
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

}
