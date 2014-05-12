package com.ldapper.event;

import com.ldapper.data.LDAPPERBean;
import com.ldapper.event.LDAPPEREventListener.EventType;

public class LDAPPEREventAdd extends LDAPPEREvent {

	private LDAPPERBean addedObj;
	
	public LDAPPEREventAdd(EventType eventType, String originatorName, 
			boolean completed, LDAPPERBean addedObj) {
		super(eventType, originatorName, completed);
		this.addedObj = addedObj;
	}

	public LDAPPERBean getAddedObj() {
		return addedObj;
	}
	
}
