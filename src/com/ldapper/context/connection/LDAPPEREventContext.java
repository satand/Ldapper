package com.ldapper.context.connection;

import java.util.Properties;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.event.EventContext;
import javax.naming.event.NamingExceptionEvent;
import javax.naming.ldap.UnsolicitedNotificationEvent;
import javax.naming.ldap.UnsolicitedNotificationListener;

import org.apache.log4j.Logger;

import com.ldapper.context.connection.ConnectionEvent.ConnectionEventType;
import com.sun.jndi.ldap.LdapCtx;
import com.sun.jndi.ldap.LdapURL;

public class LDAPPEREventContext {

	private static transient final Logger log = Logger
			.getLogger(LDAPPEREventContext.class);

	public static final String PROP_CONNECTION_OBSERVER =
			"com.ldapper.context.connectionObserver";

	private class EventHostListener implements UnsolicitedNotificationListener, Runnable {

		private boolean isAvailable = true;
		private String ldapUrl;
		private String host;
		private int port;
		private LdapCtx eventDirCtx;

		public EventHostListener(String ldapUrl) throws NamingException {
			this.ldapUrl = ldapUrl;

			LdapURL ldpaUrl = new LdapURL(ldapUrl);
			host = ldpaUrl.getHost();
			port = ldpaUrl.getPort();

		}

		@Override
		public void run() {
			try {
				while (isAvailable) {
					try {
						createEventDirContext();
					}
					catch (Exception e) {
						log.error(
								new StringBuilder().append(EventHostListener.this)
										.append(" - Error creating event context: ")
										.append(e.getMessage()).toString(), e);

						closeEventDirContext();

						try {
							Thread.sleep(1000);
						}
						catch (InterruptedException ie) {
						}

						continue;
					}

					// Block keep alive process
					try {
						synchronized (ldapUrl) {
							ldapUrl.wait();
						}
					}
					catch (InterruptedException e) {
					}

					closeEventDirContext();
				}
			}
			finally {
				closeEventDirContext();
			}
		}

		private void createEventDirContext() throws NamingException {
			// Create event dir context
			eventDirCtx = new LdapCtx("", host, port, env, false);
			// Register unsolicited notification listener
			eventDirCtx.addNamingListener("", EventContext.OBJECT_SCOPE, this);

			// Send server connection up event
			connectionObserver.notifyConnectionEvent(new ConnectionEvent(ldapUrl,
					ConnectionEventType.UP, LDAPPEREventContext.this));
		}

		public void closeEventDirContext() {
			if (eventDirCtx != null) {
				try {
					eventDirCtx.close();
				}
				catch (NamingException e) {
				}
				eventDirCtx = null;
			}
		}

		public void close() {
			isAvailable = false;

			synchronized (ldapUrl) {
				ldapUrl.notify();
			}
		}

		@Override
		public void notificationReceived(UnsolicitedNotificationEvent evt) {
			log.warn(new StringBuilder().append(EventHostListener.this)
					.append(" - Received unsolicited notification: ").append(evt)
					.toString());
		}

		@Override
		public void namingExceptionThrown(NamingExceptionEvent evt) {
			if (evt.getException() instanceof CommunicationException) {
				// Connection with server is down
				log.warn(new StringBuilder().append(EventHostListener.this)
						.append(" - Received connection notification: ")
						.append(evt.getException().getMessage()).toString());

				// Send server connection down event
				connectionObserver
						.notifyConnectionEvent(new ConnectionEvent(ldapUrl,
								ConnectionEventType.DOWN, LDAPPEREventContext.this));

				// start keep alive process
				synchronized (ldapUrl) {
					ldapUrl.notify();
				}
			} else {
				log.warn(
						new StringBuilder().append(EventHostListener.this)
								.append(" - Received unsolicited exception: ")
								.append(evt.getException().getMessage()).toString(),
						evt.getException());
			}
		}

		@Override
		public String toString() {
			return new StringBuilder().append("Event listener of '").append(ldapUrl)
					.append("'").toString();
		}
	}

	private boolean closed = false;
	private Properties env;
	private LDAPPERConnectionObserver connectionObserver;
	private EventHostListener[] listeners;

	/**
	 * Create a default LDAPPEREventContext
	 */
	public static LDAPPEREventContext createLDAPPEREventContext(String username,
			String password, String url, LDAPPERConnectionObserver connectionObserver) {
		return createLDAPPEREventContext(username, password, url,
				connectionObserver, null);
	}

	/**
	 * Create a LDAPPEREventContext with optional properties
	 */
	public static LDAPPEREventContext createLDAPPEREventContext(String username,
			String password, String url,
			LDAPPERConnectionObserver connectionObserver, Properties props) {
		if (username == null) {
			throw new RuntimeException("Username is null!");
		}
		if (password == null) {
			throw new RuntimeException("Password is null!");
		}
		if (url == null) {
			throw new RuntimeException("URL is null!");
		}
		if (connectionObserver == null) {
			throw new RuntimeException("LDAPPERConnectionObserver is null!");
		}

		if (props == null) {
			props = new Properties();
		}
		props.put(Context.SECURITY_PRINCIPAL, username);
		props.put(Context.SECURITY_CREDENTIALS, password);
		props.put(Context.PROVIDER_URL, url);
		props.put(PROP_CONNECTION_OBSERVER, connectionObserver);

		// Create and return LDAPPEREventContext
		return new LDAPPEREventContext(props);
	}

	public LDAPPEREventContext(Properties env) {
		initEnvirnonment(env);

		this.env = (Properties) env.clone();

		try {
			init(env);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void init(Properties env) throws NamingException {
		// Get LDAPPERConnectionObserver
		connectionObserver =
				(LDAPPERConnectionObserver) env.get(PROP_CONNECTION_OBSERVER);

		// Create listeners
		String providerUrl = env.getProperty(Context.PROVIDER_URL);
		String[] ldapUrls = LdapURL.fromList(providerUrl);
		listeners = new EventHostListener[ldapUrls.length];
		for (int i = 0; i < ldapUrls.length; i++) {
			listeners[i] = new EventHostListener(ldapUrls[i]);
		}
	}

	protected void initEnvirnonment(Properties env) {
		if (env == null) {
			throw new RuntimeException("Configuration properties are null!");
		}

		if (env.get(Context.SECURITY_PRINCIPAL) == null) {
			throw new RuntimeException(Context.SECURITY_PRINCIPAL
					+ " property is null!");
		}
		if (env.get(Context.SECURITY_CREDENTIALS) == null) {
			throw new RuntimeException(Context.SECURITY_CREDENTIALS
					+ " property is null!");
		}
		if (env.get(Context.PROVIDER_URL) == null) {
			throw new RuntimeException(Context.PROVIDER_URL + " property is null!");
		}
		if (env.get(PROP_CONNECTION_OBSERVER) == null) {
			throw new RuntimeException(PROP_CONNECTION_OBSERVER
					+ " property is null!");
		}

		Object prop = env.get(Context.INITIAL_CONTEXT_FACTORY);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(Context.INITIAL_CONTEXT_FACTORY,
					"com.sun.jndi.ldap.LdapCtxFactory");
		}

		prop = env.get(Context.SECURITY_AUTHENTICATION);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(Context.SECURITY_AUTHENTICATION, "simple");
		}

		prop = env.get(Context.BATCHSIZE);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(Context.BATCHSIZE, "21");
		}

		prop = env.get(Context.REFERRAL);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(Context.REFERRAL, "ignore");
		}
	}

	public void close() {
		try {
			for (EventHostListener listener : listeners) {
				listener.close();
			}
		}
		finally {
			closed = false;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		if (closed) {
			return;
		}

		try {
			close();
		}
		catch (Exception e) {
			log.error("Error finalizing '" + this + "': " + e.getMessage(), e);
		}
		finally {
			log.warn("Called finalize() on '" + this.getClass().getName()
					+ "'! Check to call close() instead");
		}
	}

}
