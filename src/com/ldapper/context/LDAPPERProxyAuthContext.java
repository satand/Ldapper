package com.ldapper.context;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;

import org.apache.log4j.Logger;

import com.ldapper.context.control.ProxyAuthorizationControl;

public class LDAPPERProxyAuthContext extends LDAPPERContext {

	private static final long serialVersionUID = 4560344534444523849L;

	public static final String PROP_AUTHENTICATION_IDENTITY =
			"com.ldapper.context.authIdentityDN";

	private static transient final Logger log = Logger
			.getLogger(LDAPPERProxyAuthContext.class);

	private String authIdentityDN;
	private transient Control[] requestControls;

	/**
	 * Create a default LDAPPERProxyAuth context
	 */
	public static LDAPPERProxyAuthContext createLDAPContext(String username,
			String password, String url, String authIdentityDN) {
		return createLDAPContext(username, password, url, authIdentityDN, null);
	}

	/**
	 * Create a LDAPPERProxyAuth context with optional properties
	 */
	public static LDAPPERProxyAuthContext createLDAPContext(String username,
			String password, String url, String authIdentityDN, Properties props) {
		if (username == null) {
			throw new RuntimeException("Username is null!");
		}
		if (password == null) {
			throw new RuntimeException("Password is null!");
		}
		if (url == null) {
			throw new RuntimeException("URL is null!");
		}
		if (authIdentityDN == null) {
			throw new RuntimeException("Authentication Identity DN is null!");
		}

		if (props == null) {
			props = new Properties();
		}
		props.put(Context.SECURITY_PRINCIPAL, username);
		props.put(Context.SECURITY_CREDENTIALS, password);
		props.put(Context.PROVIDER_URL, url);
		props.put(PROP_AUTHENTICATION_IDENTITY, authIdentityDN);

		return new LDAPPERProxyAuthContext(props);
	}

	public LDAPPERProxyAuthContext(Properties env) {
		super(env);
	}

	/**
	 * Do not use (for internal use only)
	 * */
	public LDAPPERProxyAuthContext() {};

	protected void init(Properties env) throws Exception {
		super.init(env);

		if (!isSupportProxyAuthControl()) {
			throw new Exception(
					"LDAP Server doesn't support Proxied Authorization Control!");
		}

		authIdentityDN = env.getProperty(PROP_AUTHENTICATION_IDENTITY);
		if (authIdentityDN == null) {
			String errMsg = "Authentication Identity DN is null!";
			log.error(errMsg);
			throw new Exception(errMsg);
		}
	}

	protected Control[] getRequestControls(LdapContext ctx) throws Exception {
		if (requestControls == null) {
			ProxyAuthorizationControl authControl =
					new ProxyAuthorizationControl(authIdentityDN,
							ctx.getNameInNamespace());
			requestControls = new Control[] { authControl };
		}
		return requestControls;
	}

	public LDAPPERProxyAuthContext newInstance(Control[] reqCtls) throws Exception {
		LDAPPERProxyAuthContext newCtx = new LDAPPERProxyAuthContext();
		exportConfig(newCtx, reqCtls);
		return newCtx;
	}

	protected <T extends LDAPPERContext> void exportConfig(T destCtx,
			Control[] reqCtls) throws Exception {
		if (destCtx instanceof LDAPPERProxyAuthContext) {
			((LDAPPERProxyAuthContext) destCtx).authIdentityDN = authIdentityDN;
		}
		super.exportConfig(destCtx, reqCtls);
	}

	public String getAuthIdentityDN() {
		return authIdentityDN;
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		authIdentityDN = (String) in.readObject();

		super.readExternal(in);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(authIdentityDN);

		super.writeExternal(out);
	}
}
