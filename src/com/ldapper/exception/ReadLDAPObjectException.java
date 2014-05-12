package com.ldapper.exception;

import com.ldapper.data.LDAPPERBean;

public class ReadLDAPObjectException extends RuntimeException {

	private static final long serialVersionUID = 107478893810072994L;

	private LDAPPERBean obj;

	public ReadLDAPObjectException(LDAPPERBean obj, String message) {
		super(message);
		this.obj = obj;
	}
	
	public ReadLDAPObjectException(LDAPPERBean obj, String message, Throwable cause) {
		super(message, cause);
		this.obj = obj;
	}

	public LDAPPERBean getObj() {
		return obj;
	}	

}
