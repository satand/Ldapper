package com.ldapper.event;

import com.ldapper.data.LDAPPERBean;
import com.ldapper.event.LDAPPEREventListener.EventType;

public class LDAPPEREventRemove extends LDAPPEREvent {

	private LDAPPERBean removedObj;
	
	public LDAPPEREventRemove(EventType eventType, String originatorName, 
			boolean completed, LDAPPERBean removedObj) {
		super(eventType, originatorName, completed);
		this.removedObj = removedObj;
	}

	public LDAPPERBean getRemovedObj() {
		return removedObj;
	}
	
}
