package com.ldapper.context.connectionPool;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.directory.Attribute;

import org.apache.log4j.Logger;

import com.ldapper.context.ILDAPPERContext;
import com.ldapper.context.LDAPPERContext;
import com.ldapper.context.LDAPPERProxyAuthContext;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.exception.InvalidDN;
import com.ldapper.utility.LDAPUtilities;

public class LDAPPERConnectionPool implements Serializable {

	private static final long serialVersionUID = -4584350993436334263L;

	private static final Logger log = Logger.getLogger(LDAPPERConnectionPool.class);

	public static final String PROP_USE_CONNECTION_POOL =
			"com.sun.jndi.ldap.connect.pool";
	public static final String PROP_PROXY_AUTH_CONTEXT_CACHE =
			"com.ldapper.connection.pool.proxyAuthCtx.cache";

	private static final int DEFAULT_PROXY_AUTH_CONTEXT_CACHE = 100;

	private Properties env;
	private transient LDAPPERContext ctx;
	private transient HashMap<String, LDAPPERProxyAuthContext> proxyAuthContextCache;
	private transient Integer proxyAuthContextCacheMaxSize;

	/**
	 * Create a default LDAPPERConnectionPool
	 */
	public static LDAPPERConnectionPool createLDAPPERConnectionPool(String username,
			String password, String url) {
		return createLDAPPERConnectionPool(username, password, url, null);
	}

	/**
	 * Create a LDAPPERConnectionPool with optional properties
	 */
	public static LDAPPERConnectionPool createLDAPPERConnectionPool(String username,
			String password, String url, Properties props) {
		if (username == null) {
			throw new RuntimeException("Username is null!");
		}
		if (password == null) {
			throw new RuntimeException("Password is null!");
		}
		if (url == null) {
			throw new RuntimeException("URL is null!");
		}

		if (props == null) {
			props = new Properties();
		}
		props.put(Context.SECURITY_PRINCIPAL, username);
		props.put(Context.SECURITY_CREDENTIALS, password);
		props.put(Context.PROVIDER_URL, url);

		// Create and return LDAPPERConnectionPool
		return new LDAPPERConnectionPool(props);
	}

	public LDAPPERConnectionPool(Properties env) {
		initEnvirnonment(env);
		this.env = (Properties) env.clone();
	}

	public LDAPPERContext getContext() {
		if (ctx == null) {
			ctx = new LDAPPERContext((Properties) env.clone());
		}
		return ctx;
	}

	public LDAPPERContext getContext(LDAPPERBean authIdentity, String userPassword) {
		Properties props = (Properties) env.clone();
		try {
			props.put(Context.SECURITY_PRINCIPAL, authIdentity.getDN());
			props.put(Context.SECURITY_CREDENTIALS, userPassword);
		}
		catch (InvalidDN e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return new LDAPPERContext(props);
	}

	public LDAPPERProxyAuthContext getProxyAuthContext(LDAPPERBean authIdentity) {
		try {
			String authIdentityDN = authIdentity.getDN();
			LDAPPERProxyAuthContext ctx =
					getLDAPPERProxyAuthContextCache().get(authIdentityDN);
			if (ctx == null) {
				Properties props = (Properties) env.clone();
				props.put(LDAPPERProxyAuthContext.PROP_AUTHENTICATION_IDENTITY,
						authIdentity.getDN());
				ctx = new LDAPPERProxyAuthContext(props);

				getLDAPPERProxyAuthContextCache().put(authIdentityDN, ctx);
			}
			return ctx;
		}
		catch (InvalidDN e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private void initEnvirnonment(Properties env) {
		// Set use connection pool on environment
		env.put(PROP_USE_CONNECTION_POOL, "true");

		// Disable connection timeout because use connection pool's timeout
		env.put(ILDAPPERContext.PROP_CONNECTION_TIMEOUT, "0");

		// Try set supported extensions and controls on environment
		try {
			String username = env.getProperty(Context.SECURITY_PRINCIPAL);
			String password = env.getProperty(Context.SECURITY_CREDENTIALS);
			String url = env.getProperty(Context.PROVIDER_URL);
			String auth = env.getProperty(Context.SECURITY_AUTHENTICATION);
			if (auth == null || ((String) auth).trim().length() == 0) {
				auth = ILDAPPERContext.DEFAULT_AUTHENTICATION;
			}

			try {
				// Set supported extensions on environment
				Attribute supportedExtensions =
						LDAPUtilities.getSupportedExtensions(username, password,
								url, auth);
				env.put(ILDAPPERContext.PROP_SUPPORTED_EXTENSIONS,
						supportedExtensions);
			}
			catch (Exception e) {
				log.error("Error retriving supported extensions from LDAP Server. Use default supported extensions");
				env.put(ILDAPPERContext.PROP_SUPPORTED_EXTENSIONS,
						ILDAPPERContext.DEFAULT_SUPPORTED_EXTENDIONS);
			}

			try {
				// Set supported controls on environment
				Attribute supportedControls =
						LDAPUtilities.getSupportedControls(username, password, url,
								auth);
				env.put(ILDAPPERContext.PROP_SUPPORTED_CONTROLS, supportedControls);
			}
			catch (Exception e) {
				log.error("Error retriving supported controls from LDAP Server. Use default supported controls");
				env.put(ILDAPPERContext.PROP_SUPPORTED_CONTROLS,
						ILDAPPERContext.DEFAULT_SUPPORTED_CONTROLS);
			}
		}
		catch (Exception e) {
			log.error("Error retriving supported extensions and controls from LDAP Server. Use default values");
			env.put(ILDAPPERContext.PROP_SUPPORTED_EXTENSIONS,
					ILDAPPERContext.DEFAULT_SUPPORTED_EXTENDIONS);
			env.put(ILDAPPERContext.PROP_SUPPORTED_CONTROLS,
					ILDAPPERContext.DEFAULT_SUPPORTED_CONTROLS);
		}
	}

	private HashMap<String, LDAPPERProxyAuthContext> getLDAPPERProxyAuthContextCache() {
		if (proxyAuthContextCache == null) {
			proxyAuthContextCacheMaxSize =
					(Integer) env.get(PROP_PROXY_AUTH_CONTEXT_CACHE);
			if (proxyAuthContextCacheMaxSize == null) {
				proxyAuthContextCacheMaxSize = DEFAULT_PROXY_AUTH_CONTEXT_CACHE;
			}

			proxyAuthContextCache =
					new LinkedHashMap<String, LDAPPERProxyAuthContext>(
							proxyAuthContextCacheMaxSize + 1, .75F, true) {

						private static final long serialVersionUID =
								-6612053636511019619L;

						// This method is called just after a new entry has been
						// added
						public boolean removeEldestEntry(
								Map.Entry<String, LDAPPERProxyAuthContext> eldest) {
							return size() > proxyAuthContextCacheMaxSize;
						}
					};
		}
		return proxyAuthContextCache;
	}
}
