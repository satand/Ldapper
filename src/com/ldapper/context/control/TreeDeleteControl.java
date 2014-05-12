package com.ldapper.context.control;

import javax.naming.ldap.BasicControl;

public class TreeDeleteControl extends BasicControl {

	private static final long serialVersionUID = -9145321436984993695L;
	
	/**
	 * The control OID for the transaction specification control.
	 */
	public static final String OID_TREE_DELETE_CONTROL = "1.2.840.113556.1.4.805";

	public TreeDeleteControl() {
		super(OID_TREE_DELETE_CONTROL, true, null);
	}

}