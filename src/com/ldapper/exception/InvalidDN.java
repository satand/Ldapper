package com.ldapper.exception;

public class InvalidDN extends Exception {

	private static final long serialVersionUID = 537756387366806300L;

	private String dn;

	public InvalidDN(String dn, String message) {
		this(message);
		this.dn=dn;
	}
	
	public InvalidDN(String message) {
		super(message);
	}
	
	public String getDN() {
		return dn;
	}
}
