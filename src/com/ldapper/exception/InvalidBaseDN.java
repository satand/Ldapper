package com.ldapper.exception;

public class InvalidBaseDN extends InvalidDN {

	private static final long serialVersionUID = -7968945088617568126L;

	private String baseDN;

	public InvalidBaseDN(String baseDN, String dn, String message) {
		this(dn, message);
		this.baseDN=baseDN;
	}

	public InvalidBaseDN(String dn, String message) {
		super(dn, message);
	}

	public InvalidBaseDN(String message) {
		super(message);
	}
	
	public String getBaseDN() {
		return baseDN;
	}
}
