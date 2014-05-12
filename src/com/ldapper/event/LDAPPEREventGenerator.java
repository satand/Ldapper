package com.ldapper.event;

import java.io.Serializable;
import java.util.Hashtable;

import com.ldapper.data.LDAPPERBean;
import com.ldapper.event.LDAPPEREventListener.EventType;
import com.ldapper.event.LDAPPEREventListener.Operation;

public class LDAPPEREventGenerator implements Serializable {

	private static final long serialVersionUID = 939337374717628252L;

	private LDAPPEREventListener ldapperEventlistener;
	private Hashtable<Class<? extends LDAPPERBean>, EventType[]> eventCache;

	public LDAPPEREventGenerator(LDAPPEREventListener ldapperEventlistener) {
		this.ldapperEventlistener = ldapperEventlistener;
		this.eventCache = new Hashtable<Class<? extends LDAPPERBean>, EventType[]>();
	}

	/**
	 * Get EventType for an couple Operation - Bean Class. If it returns null
	 * any event should produce.
	 */
	public EventType getEventType(Operation op, Class<? extends LDAPPERBean> objClass) {

		EventType[] classEventsType = eventCache.get(objClass);
		if (classEventsType == null) {
			classEventsType = new EventType[4];
			eventCache.put(objClass, classEventsType);
		}

		EventType evType = classEventsType[op.ordinal()];
		if (evType == null) {
			evType = ldapperEventlistener.checkEvent(op, objClass);
			if (evType == null) {
				evType = EventType.NO_EVENT;
			}
			classEventsType[op.ordinal()] = evType;
		}
		return evType;
	}

	/**
	 * Send add event to registered LDAPPEREventListener
	 */
	public void sendAddEvent(LDAPPEREventAdd event) {
		ldapperEventlistener.onAddEvent(event);
	}

	/**
	 * Send update event to registered LDAPPEREventListener
	 */
	public void sendUpdateEvent(LDAPPEREventUpdate event) {
		ldapperEventlistener.onUpdateEvent(event);
	}

	/**
	 * Send remove event to registered LDAPPEREventListener
	 */
	public void sendRemoveEvent(LDAPPEREventRemove event) {
		ldapperEventlistener.onRemoveEvent(event);
	}

	/**
	 * Send changePassword event to registered LDAPPEREventListener
	 */
	public void sendChangePasswordEvent(LDAPPEREventChangePassword event) {
		ldapperEventlistener.onChangePasswordEvent(event);
	}

}
