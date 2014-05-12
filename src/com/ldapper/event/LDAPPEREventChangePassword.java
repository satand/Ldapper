package com.ldapper.event;

import com.ldapper.event.LDAPPEREventListener.EventType;

public class LDAPPEREventChangePassword extends LDAPPEREvent {

	private String entityDN; 
	private String oldPassword;
	private String newPassword;
	
	public LDAPPEREventChangePassword(EventType eventType, String originatorName, 
			String userDN, String oldPassword, String newPassword) {
		super(eventType, originatorName, false);
		this.entityDN = userDN;
		this.oldPassword = oldPassword;
		this.newPassword = newPassword;
	}

	public String getEntityDN() {
		return entityDN;
	}
	
	public String getOldPassword() {
		return oldPassword;
	}
	
	public String getNewPassword() {
		return newPassword;
	}
	
}
