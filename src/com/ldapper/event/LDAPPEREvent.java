package com.ldapper.event;

import com.ldapper.event.LDAPPEREventListener.EventType;

public abstract class LDAPPEREvent {
	private EventType eventType;
	private String originatorName;
	private boolean completed;
	
	public LDAPPEREvent(EventType eventType, String originatorName, boolean completed) {
		this.eventType = eventType;
		this.originatorName = originatorName;
		this.completed = completed;
	}
	
	public EventType getEventType() {
		return eventType;
	}
	
	public String getOriginatorName() {
		return originatorName;
	}
	
	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

}
