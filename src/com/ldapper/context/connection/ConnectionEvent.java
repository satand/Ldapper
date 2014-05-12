package com.ldapper.context.connection;

import java.io.Serializable;

public class ConnectionEvent implements Serializable {

	private static final long serialVersionUID = 7781481279394540701L;

	public enum ConnectionEventType {
		UP,
		DOWN
	}

	private String ldapUrl;
	private ConnectionEventType type;
	private LDAPPEREventContext eventContext;

	public ConnectionEvent(String ldapURL, ConnectionEventType type,
			LDAPPEREventContext eventContext) {
		this.ldapUrl = ldapURL;
		this.type = type;
		this.eventContext = eventContext;
	}

	public String getUrl() {
		return ldapUrl;
	}

	public ConnectionEventType getType() {
		return type;
	}

	public LDAPPEREventContext getEventContext() {
		return eventContext;
	}

}
