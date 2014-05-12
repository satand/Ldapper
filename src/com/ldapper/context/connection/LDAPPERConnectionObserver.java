package com.ldapper.context.connection;

public interface LDAPPERConnectionObserver {

	public void notifyConnectionEvent(ConnectionEvent event);
	
}
