package com.ldapper.event;

import com.ldapper.data.LDAPPERBean;
import com.ldapper.event.LDAPPEREventListener.EventType;

public class LDAPPEREventUpdate extends LDAPPEREvent {

	private LDAPPERBean updatedObj;
	private LDAPPERBean addedDeltaObj;
	private LDAPPERBean removedDeltaObj;
	
	public LDAPPEREventUpdate(EventType eventType, String originatorName, 
			boolean completed, LDAPPERBean updatedObj, 
			LDAPPERBean addedDeltaObj, LDAPPERBean removedDeltaObj) {
		super(eventType, originatorName, completed);
		this.updatedObj = updatedObj;
		this.addedDeltaObj = addedDeltaObj;
		this.removedDeltaObj = removedDeltaObj;
	}

	public LDAPPERBean getUpdatedObj() {
		return updatedObj;
	}
	
	public LDAPPERBean getAddedDeltaObj() {
		return addedDeltaObj;
	}
	
	public LDAPPERBean getRemovedDeltaObj() {
		return removedDeltaObj;
	}
}
