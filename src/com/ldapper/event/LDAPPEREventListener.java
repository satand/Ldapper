package com.ldapper.event;

import java.io.Serializable;

import com.ldapper.data.LDAPPERBean;

public interface LDAPPEREventListener extends Serializable {

	public enum Operation {
		ADD,
		REMOVE,
		UPDATE,
		CHANGE_PASSWORD;
	}

	public interface EventType {

		public boolean sendEventOnComplete();

		public static final EventType NO_EVENT = new EventType() {

			@Override
			public boolean sendEventOnComplete() {
				return false;
			}
		};

	}

	public EventType checkEvent(Operation opType,
			Class<? extends LDAPPERBean> objClass);

	public void onAddEvent(LDAPPEREventAdd addEvent);

	public void onRemoveEvent(LDAPPEREventRemove removeEvent);

	public void onUpdateEvent(LDAPPEREventUpdate updateEvent);

	public void onChangePasswordEvent(LDAPPEREventChangePassword changePasswordEvent);
}
