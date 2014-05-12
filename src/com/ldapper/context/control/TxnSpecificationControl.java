package com.ldapper.context.control;

import javax.naming.ldap.BasicControl;

public class TxnSpecificationControl extends BasicControl {

	private static final long serialVersionUID = 6494611215042345347L;
	
	/**
	 * The control OID for the transaction specification control.
	 */
	public static final String OID_TRANSACTION_SPECIFICATION_CONTROL = "2.16.840.1.113730.3.5.2";

	public TxnSpecificationControl(String txnID) {
		super(OID_TRANSACTION_SPECIFICATION_CONTROL, true, null);
		super.value = getEncodedValue(txnID);
	}

	private byte[] getEncodedValue(String txnID) {
		if (txnID==null || txnID.trim().length()==0) {
			throw new RuntimeException("Transaction ID is null or empty!");
		}
		return txnID.getBytes();
	}
}