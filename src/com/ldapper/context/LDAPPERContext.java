package com.ldapper.context;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Queue;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.ContextNotEmptyException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.SortControl;

import org.apache.log4j.Logger;

import com.ldapper.context.control.ProxyAuthorizationControl;
import com.ldapper.context.control.TreeDeleteControl;
import com.ldapper.context.control.TxnSpecificationControl;
import com.ldapper.context.control.VLVRequestControl;
import com.ldapper.context.extension.EndTxnRequest;
import com.ldapper.context.extension.ModifyPasswordRequest;
import com.ldapper.context.extension.ModifyPasswordResponse;
import com.ldapper.context.extension.StartTxnRequest;
import com.ldapper.context.search.PagedResults;
import com.ldapper.context.search.PagedResultsForAttributes;
import com.ldapper.context.search.PagedResultsForFilter;
import com.ldapper.context.search.PagedResultsForFilterExpr;
import com.ldapper.context.search.SearchResultEnumeration;
import com.ldapper.context.search.SearchScope;
import com.ldapper.context.search.SimplePagedResultsForAttributes;
import com.ldapper.context.search.SimplePagedResultsForFilter;
import com.ldapper.context.search.SimplePagedResultsForFilterExpr;
import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.context.template.NestedObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;
import com.ldapper.data.TargetAttribute;
import com.ldapper.event.LDAPPEREventAdd;
import com.ldapper.event.LDAPPEREventChangePassword;
import com.ldapper.event.LDAPPEREventGenerator;
import com.ldapper.event.LDAPPEREventListener;
import com.ldapper.event.LDAPPEREventListener.EventType;
import com.ldapper.event.LDAPPEREventListener.Operation;
import com.ldapper.event.LDAPPEREventRemove;
import com.ldapper.event.LDAPPEREventUpdate;
import com.ldapper.exception.InvalidDN;
import com.ldapper.utility.LDAPUtilities;

public class LDAPPERContext implements ILDAPPERContext, Externalizable {

	private static final long serialVersionUID = 9196380817252015959L;

	private static transient final Logger log = Logger
			.getLogger(LDAPPERContext.class);

	Properties env;
	// transient LdapContext ctx;
	LDAPPEREventGenerator eventGenerator;

	Attribute supportedControls;
	transient Boolean supportTreeDeleteControl;
	transient Boolean supportSortControl;
	transient Boolean supportVLVControl;
	transient Boolean supportSimplePagedResultsControl;
	transient Boolean supportProxyAuthControl;

	Attribute supportedExtensions;
	transient Boolean supportTxnExtension;
	transient Boolean supportModifyPasswordExtension;

	transient Boolean supportedRenameSubTree;

	/**
	 * Create a default LDAPPER context
	 */
	public static LDAPPERContext createLDAPContext(String username, String password,
			String url) {
		return createLDAPContext(username, password, url, null);
	}

	/**
	 * Create a LDAPPER context with optional properties
	 */
	public static LDAPPERContext createLDAPContext(String username, String password,
			String url, Properties props) {
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

		// Create and return LDAPPERContext
		return new LDAPPERContext(props);
	}

	public LDAPPERContext(Properties env) {
		// Configure environment
		initEnvirnonment(env);

		try {
			init(env);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		this.env = (Properties) env.clone();
	}

	/**
	 * Do not use (for internal use only)
	 * */
	public LDAPPERContext() {};

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

		Object prop = env.get(PROP_CONTEXT_FACTORY);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_CONTEXT_FACTORY, DEFAULT_CONTEXT_FACTORY);
		}

		prop = env.get(PROP_SECURITY_AUTHENTICATION);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_SECURITY_AUTHENTICATION, DEFAULT_AUTHENTICATION);
		}

		prop = env.get(PROP_CONNECTION_TIMEOUT);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_CONNECTION_TIMEOUT, DEFAULT_TIMEOUT);
		}

		prop = env.get(PROP_BATCHSIZE);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_BATCHSIZE, DEFAULT_BATCHSIZE);
		}

		prop = env.get(PROP_CONTEXT_REFERRAL);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_CONTEXT_REFERRAL, DEFAULT_CONTEXT_REFERRAL);
		}

		prop = env.get(PROP_CONTROL_FACTORIES);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_CONTROL_FACTORIES, DEFAULT_CONTROL_FACTORIES);
		}

		prop = env.get(PROP_SUPPORTED_RENAME_SUBTREE);
		if (prop == null || ((String) prop).trim().length() == 0) {
			env.put(PROP_SUPPORTED_RENAME_SUBTREE, DEFAULT_SUPPORTED_RENAME_SUBTREE);
		}
	}

	protected void init(Properties env) throws Exception {
		supportedControls = (Attribute) env.get(PROP_SUPPORTED_CONTROLS);
		supportedExtensions = (Attribute) env.get(PROP_SUPPORTED_EXTENSIONS);

		/*
		 * Note: Remove PROP_EVENT_GENERATOR and PROP_EVENT_LISTENER from
		 * environment to don't have problems in phase of context serialization.
		 */

		// Set LDAPPER event generator
		Object prop = env.remove(PROP_EVENT_GENERATOR);
		if (prop != null) {
			eventGenerator = (LDAPPEREventGenerator) prop;
		} else {
			// Create LDAPPER event generator if there is a LDAPPER event
			// listener
			prop = env.remove(PROP_EVENT_LISTENER);
			if (prop != null) {
				eventGenerator =
						new LDAPPEREventGenerator((LDAPPEREventListener) prop);
			}
		}
	}

	protected Control[] getRequestControls(LdapContext ctx) throws Exception {
		return null;
	}

	/**
	 * Creates a new instance of this context initialized using request
	 * controls.
	 * 
	 * This method is a convenience method for creating a new instance of this
	 * context for the purposes of multithreaded access. For example, if
	 * multiple threads want to use different context request controls, each
	 * thread may use this method to get its own copy of this context and
	 * set/get context request controls without having to synchronize with other
	 * threads.
	 * <p>
	 * The new context has the same environment properties and connection
	 * request controls as this context. See the class description for details.
	 * Implementations might also allow this context and the new context to
	 * share the same network connection or other resources if doing so does not
	 * impede the independence of either context.
	 * 
	 * @param requestControls
	 *            The possibly null request controls to use for the new context.
	 *            If null, the context is initialized with no request controls.
	 * 
	 * @return A non-null <tt>LDAPPERContext</tt> instance.
	 * @exception Exception
	 *                If an error occurred while creating the new instance.
	 * @see InitialLdapContext
	 */
	public LDAPPERContext newInstance(Control[] reqCtls) throws Exception {
		LDAPPERContext newCtx = new LDAPPERContext();
		exportConfig(newCtx, reqCtls);
		return newCtx;
	}

	protected <T extends LDAPPERContext> void exportConfig(T destCtx,
			Control[] reqCtls) throws Exception {
		destCtx.env = env;
		destCtx.supportedControls = supportedControls;
		destCtx.supportedExtensions = supportedExtensions;
		destCtx.supportTreeDeleteControl = supportTreeDeleteControl;
		destCtx.supportSortControl = supportSortControl;
		destCtx.supportVLVControl = supportVLVControl;
		destCtx.supportSimplePagedResultsControl = supportSimplePagedResultsControl;
		destCtx.supportProxyAuthControl = supportProxyAuthControl;
		destCtx.supportTxnExtension = supportTxnExtension;
		destCtx.supportModifyPasswordExtension = supportModifyPasswordExtension;
		destCtx.supportedRenameSubTree = supportedRenameSubTree;
		destCtx.eventGenerator = eventGenerator;
	}

	public void setEventGenerator(LDAPPEREventGenerator eventGenerator) {
		this.eventGenerator = eventGenerator;
	}

	public LDAPPEREventGenerator getEventGenerator() {
		return eventGenerator;
	}

	/**
	 * Retrieves if this LDAPPER context supports tree delete operation
	 */
	public boolean isSupportTreeDeleteControl() {
		if (supportTreeDeleteControl == null) {
			// Check if server supports Tree Delete Control
			supportTreeDeleteControl =
					hasControlSupport(TreeDeleteControl.OID_TREE_DELETE_CONTROL);
		}
		return supportTreeDeleteControl;
	}

	/**
	 * Retrieves if this LDAPPER context supports search sorting operation
	 */
	public boolean isSupportSortControl() {
		if (supportSortControl == null) {
			// Check if server supports Sort Control (Server Side Sorting)
			supportSortControl = hasControlSupport(SortControl.OID);
		}
		return supportSortControl;
	}

	/**
	 * Retrieves if this LDAPPER context supports paged result search operation
	 */
	public boolean isSupportVLVControl() {
		if (supportVLVControl == null) {
			// Check if server supports VLV Control (Virtual List View)
			if (isSupportSortControl()) {
				supportVLVControl =
						hasControlSupport(VLVRequestControl.OID_VLV_REQUEST_CONTROL);
			}
		}
		return supportVLVControl;
	}

	/**
	 * Retrieves if this LDAPPER context supports simple paged result search
	 * operation
	 */
	public boolean isSupportSimplePagedResultsControl() {
		if (supportSimplePagedResultsControl == null) {
			// Check if server supports Simple Paged Results Control
			supportSimplePagedResultsControl =
					hasControlSupport(PagedResultsControl.OID);
		}
		return supportSimplePagedResultsControl;
	}

	/**
	 * Retrieves if this LDAPPER context supports proxy authorization operation
	 */
	public boolean isSupportProxyAuthControl() {
		if (supportProxyAuthControl == null) {
			// Check if server supports Proxy Authorization Control
			supportProxyAuthControl =
					hasControlSupport(ProxyAuthorizationControl.OID_PROXYAUTH_REQUEST_CONTROL);
		}
		return supportProxyAuthControl;
	}

	/**
	 * Retrieves if this LDAPPER context supports LDAP transactions
	 */
	public boolean isSupportTxnExtension() {
		if (supportTxnExtension == null) {
			// Check if server supportsTransaction Extension
			supportTxnExtension =
					hasExtensionSupport(StartTxnRequest.OID_START_TRANSACTION_REQUEST)
							&& hasExtensionSupport(EndTxnRequest.OID_END_TRANSACTION_REQUEST)
							&& hasControlSupport(TxnSpecificationControl.OID_TRANSACTION_SPECIFICATION_CONTROL);
		}
		return supportTxnExtension;
	}

	/**
	 * Retrieves if this LDAPPER context supports Modify Password Extension
	 */
	public boolean isSupportModifyPasswordExtension() {
		if (supportModifyPasswordExtension == null) {
			// Check if server supports Modify Password Extension
			supportModifyPasswordExtension =
					hasExtensionSupport(ModifyPasswordRequest.OID_PASSWORD_MODIFY_REQUEST);
		}
		return supportModifyPasswordExtension;
	}

	/**
	 * Retrieves if this LDAPPER context supports tree node rename operation
	 */
	public boolean isSupportRenameSubTree() {
		if (supportedRenameSubTree == null) {
			supportedRenameSubTree =
					Boolean.parseBoolean((String) env.getProperty(
							PROP_SUPPORTED_RENAME_SUBTREE,
							DEFAULT_SUPPORTED_RENAME_SUBTREE));
		}
		return supportedRenameSubTree;
	}

	private LdapContext getTemporaryContext() throws Exception {
		LdapContext ctx = new InitialLdapContext(env, null);
		ctx.setRequestControls(getRequestControls(ctx));
		return ctx;
	}

	/*
	 * Base Operation Methods
	 */

	/*
	 * Public methods
	 */

	public Properties getEnvironment() {
		return env;
	}

	public String getAuthIdentityDN() {
		return env.getProperty(Context.SECURITY_PRINCIPAL);
	}

	public boolean checkAuthentication() throws Exception {
		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();
			return true;
		}
		catch (AuthenticationException e) {
			return false;
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public ExtendedResponse extendedOperation(ExtendedRequest request)
			throws Exception {
		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();
			return ctx.extendedOperation(request);
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public String changeUserPassword(LDAPPERBean user, String oldPassword,
			String newPassword) throws Exception {
		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();

			String userDN = user.getDN();
			try {
				String passwordResult;
				if (isSupportModifyPasswordExtension()) {
					String userIdentity = userDN;
					String ctxDNName = ctx.getNameInNamespace();
					if (ctxDNName != null && ctxDNName.trim().length() > 0) {
						userIdentity += "," + ctxDNName;
					}

					// Modify password
					ModifyPasswordResponse response =
							(ModifyPasswordResponse) extendedOperation(new ModifyPasswordRequest(
									userIdentity, oldPassword, newPassword));

					String generatedPassw = response.getGenPassword();
					passwordResult =
							generatedPassw != null ? generatedPassw : newPassword;
				} else {
					// Update userPassword attribute
					Attributes attrs = new BasicAttributes();
					if (newPassword != null && newPassword.trim().isEmpty()) {
						newPassword = null;
					}
					attrs.put("userPassword", newPassword);

					if (newPassword == null) {
						ctx.modifyAttributes(userDN, DirContext.REMOVE_ATTRIBUTE,
								attrs);
					} else {
						ctx.modifyAttributes(userDN, DirContext.REPLACE_ATTRIBUTE,
								attrs);
					}

					passwordResult = newPassword;
				}

				if (eventGenerator != null) {
					// Send an event if a related subscription exists
					EventType eventType =
							eventGenerator.getEventType(Operation.CHANGE_PASSWORD,
									user.getClass());
					if (eventType != EventType.NO_EVENT) {
						eventGenerator
								.sendChangePasswordEvent(new LDAPPEREventChangePassword(
										eventType, getEventOriginatorName(ctx),
										userDN, oldPassword, passwordResult));
					}
				}

				return passwordResult;
			}
			catch (Exception e) {
				throw new Exception("Error changing password of '" + userDN + "': "
						+ e.getMessage(), e);
			}
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}

	}

	public boolean exists(LDAPPERBean obj) throws Exception {
		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();

			// Create SearchControls
			SearchControls searchControls = new SearchControls();
			searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
			searchControls.setReturningAttributes(new String[0]);

			try {
				return ctx.search(
						obj.getDN(),
						ExternalObjTemplate.getInstance(obj)
								.addObjClassesToSearchFilter(null), searchControls)
						.hasMore();
			}
			catch (NameNotFoundException e) {
				return false;
			}
			catch (Exception ex) {
				throw ex;
			}

		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public boolean isLeaf(LDAPPERBean obj) throws Exception {
		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();

			// Create SearchControls
			SearchControls searchControls = new SearchControls();
			searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
			searchControls.setReturningAttributes(new String[0]);

			return !ctx.search(
					obj.getDN(),
					ExternalObjTemplate.getInstance(obj)
							.addObjClassesToSearchFilter(null), searchControls)
					.hasMore();
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public void add(LDAPPERBean obj) throws Exception {
		add(obj, true);
	}

	public void add(LDAPPERBean obj, boolean enableAutomaticDataAttrs)
			throws Exception {
		// Check DN object
		dnCheck(obj, obj.getDN());

		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();

			add(ctx, obj, obj.getDN(), enableAutomaticDataAttrs);
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public void update(LDAPPERBean obj, boolean removeUnspecifiedAttr,
			boolean updateSubTree, boolean removeUnspecifiedNestedObj)
			throws Exception {
		update(obj, false, removeUnspecifiedAttr, updateSubTree,
				removeUnspecifiedNestedObj, true);
	}

	public void update(LDAPPERBean obj, boolean removeUnspecifiedAttr,
			boolean updateSubTree, boolean removeUnspecifiedNestedObj,
			boolean enableAutomaticDataAttrs) throws Exception {
		update(obj, false, removeUnspecifiedAttr, updateSubTree,
				removeUnspecifiedNestedObj, enableAutomaticDataAttrs);
	}

	public void update(LDAPPERBean obj, Boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr, boolean updateSubTree,
			boolean removeUnspecifiedNestedObj) throws Exception {
		update(obj, removeUnspecifiedObj, removeUnspecifiedAttr, updateSubTree,
				removeUnspecifiedNestedObj, true);
	}

	public void update(LDAPPERBean obj, Boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr, boolean updateSubTree,
			boolean removeUnspecifiedNestedObj, boolean enableAutomaticDataAttrs)
			throws Exception {
		String subContext = obj.getDN();

		if (updateSubTree) {
			// Check DN object
			dnCheck(obj, subContext);
		}

		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();

			if (!update(ctx, obj, subContext, removeUnspecifiedObj,
					removeUnspecifiedAttr, updateSubTree,
					removeUnspecifiedNestedObj, enableAutomaticDataAttrs)) {
				add(ctx, obj, subContext, enableAutomaticDataAttrs);
			}
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public void move(LDAPPERBean origObj, LDAPPERBean destSubContextObj)
			throws Exception {
		move(origObj, destSubContextObj, true);
	}

	public void move(LDAPPERBean origObj, LDAPPERBean destSubContextObj,
			boolean enableAutomaticDataAttrs) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(origObj);

		StringBuilder destNameBuff = new StringBuilder();
		destNameBuff.append(eot.getLDAPObjectId(origObj));
		if (destSubContextObj != null) {
			destNameBuff.append(',').append(destSubContextObj.getDN());
		}

		LdapContext ctx = null;
		try {
			ctx = getTemporaryContext();

			if (isSupportRenameSubTree() || isLeaf(origObj)) {
				// Use rename command on context
				try {
					ctx.rename(origObj.getDN(), destNameBuff.toString());
					return;
				}
				catch (ContextNotEmptyException e) {
					supportedRenameSubTree = false;
				}
			}

			// Use sequence of fetch, add and remove commands
			fetch(ctx, origObj, null, null);
			add(ctx, origObj, destNameBuff.toString(), enableAutomaticDataAttrs);
			remove(ctx, origObj, false);
		}
		finally {
			if (ctx != null) {
				ctx.close();
			}
		}
	}

	public void remove(LDAPPERBean obj) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			remove(tempCtx, obj, false);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public int count(LDAPPERBean obj) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			if (!isSupportVLVControl()) {
				throw new Exception(
						"Virtual List View Control not supported by LDAP Server in use!");
			}

			PagedResults<LDAPPERBean> search =
					new PagedResultsForAttributes<LDAPPERBean>(tempCtx,
							obj.getBaseDN(), obj,
							LDAPPERTargetAttribute.emptyLDAPPERTargetAttributes,
							new String[0], false, 1, 1, null);
			int result = search.getContentCount();

			search.close();
			return result;
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> list(T obj) throws Exception {
		return listTA(obj, TargetAttribute.getTargetAttributes(null), null);
	}

	public <T extends LDAPPERBean> ArrayList<T> list(T obj,
			String[] targetAttributes, String[] targetNestedObjs) throws Exception {
		return listTA(obj, TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	public <T extends LDAPPERBean> ArrayList<T> listTA(T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return list(tempCtx, obj, targetAttributes, targetNestedObjs);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> sortedList(T obj, String[] sortBy)
			throws Exception {
		return sortedListTA(obj, TargetAttribute.getTargetAttributes(null), null,
				sortBy);
	}

	public <T extends LDAPPERBean> ArrayList<T> sortedList(T obj,
			String[] targetAttributes, String[] targetNestedObjs, String[] sortBy)
			throws Exception {
		return sortedListTA(obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	public <T extends LDAPPERBean> ArrayList<T> sortedListTA(T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return sortedList(tempCtx, obj, targetAttributes, targetNestedObjs,
					sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public void fetch(LDAPPERBean obj) throws Exception {
		fetchTA(obj, TargetAttribute.getTargetAttributes(null), null);
	}

	public void fetch(LDAPPERBean obj, String[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		fetchTA(obj, TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	public void fetchTA(LDAPPERBean obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			fetch(tempCtx, obj, targetAttributes, targetNestedObjs);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	/*
	 * Search methods with matching bean attributes
	 */

	public <T extends LDAPPERBean> SimplePagedResultsForAttributes<T> simplePagedResultsSearch(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String[] sortBy)
			throws Exception {
		return simplePagedResultsSearchTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, contentsForPage, sortBy);
	}

	public <T extends LDAPPERBean> SimplePagedResultsForAttributes<T> simplePagedResultsSearchTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception {
		if (!isSupportSimplePagedResultsControl()) {
			throw new Exception(
					"Simple Paged Results Control not supported by LDAP Server in use!");
		}

		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return new SimplePagedResultsForAttributes<T>(tempCtx, startPointObj,
					obj, targetAttributes, targetNestedObjs, contentsForPage, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> PagedResultsForAttributes<T> pagedResultsSearch(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, boolean returnObj, int contentsForPage,
			int startPageIndex, String[] sortBy) throws Exception {
		return pagedResultsSearchTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, returnObj, contentsForPage, startPageIndex, sortBy);
	}

	public <T extends LDAPPERBean> PagedResultsForAttributes<T> pagedResultsSearchTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returnObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception {
		if (!isSupportVLVControl()) {
			throw new Exception(
					"Virtual List View Control not supported by LDAP Server in use!");
		}

		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return new PagedResultsForAttributes<T>(tempCtx, startPointObj, obj,
					targetAttributes, targetNestedObjs, returnObj, contentsForPage,
					startPageIndex, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception {
		return sortedSearchEnumerationTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumerationTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return sortedSearchEnumeration(tempCtx, startPointObj, obj,
					targetAttributes, targetNestedObjs, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		return searchEnumerationTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumerationTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return searchEnumeration(tempCtx, startPointObj, obj, targetAttributes,
					targetNestedObjs);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> sortedSearch(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception {
		return sortedSearchTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	@SuppressWarnings("unchecked")
	public <T extends LDAPPERBean> ArrayList<T> sortedSearchTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		try {
			resultEnumeration =
					sortedSearchEnumerationTA(startPointObj, obj, targetAttributes,
							targetNestedObjs, sortBy);
			return (ArrayList<T>) resultEnumeration.getAllSearchResults();
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> search(LDAPPERBean startPointObj,
			T obj, String[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		return searchTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	@SuppressWarnings("unchecked")
	public <T extends LDAPPERBean> ArrayList<T> searchTA(LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		try {
			resultEnumeration =
					searchEnumerationTA(startPointObj, obj, targetAttributes,
							targetNestedObjs);
			return (ArrayList<T>) resultEnumeration.getAllSearchResults();
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}
		}
	}

	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedAttr) throws Exception {
		searchAndUpdateTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, newObj, removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception {
		searchAndUpdateTA(startPointObj, obj,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, newObj, removeUnspecifiedObj,
				removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedAttr)
			throws Exception {
		searchAndUpdateTA(startPointObj, obj, targetAttributes, targetNestedObjs,
				newObj, false, removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr) throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		LdapContext tempCtx = null;
		try {
			resultEnumeration =
					searchEnumerationTA(startPointObj, obj, targetAttributes,
							targetNestedObjs);

			tempCtx = getTemporaryContext();
			updateAllSearchResults(tempCtx, resultEnumeration, newObj,
					removeUnspecifiedObj, removeUnspecifiedAttr);
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}

			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	/*
	 * Search methods with filter
	 */

	public <T extends LDAPPERBean> SimplePagedResultsForFilter<T> simplePagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception {
		return simplePagedResultsSearchTA(startPointObj, objClass, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, contentsForPage, sortBy);
	}

	public <T extends LDAPPERBean> SimplePagedResultsForFilter<T> simplePagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String[] sortBy)
			throws Exception {
		if (!isSupportSimplePagedResultsControl()) {
			throw new Exception(
					"Simple Paged Results Control not supported by LDAP Server in use!");
		}

		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return new SimplePagedResultsForFilter<T>(tempCtx, startPointObj,
					objClass, filter, scope, targetAttributes, targetNestedObjs,
					contentsForPage, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> PagedResultsForFilter<T> pagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			boolean returningObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception {
		return pagedResultsSearchTA(startPointObj, objClass, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, returningObj, contentsForPage, startPageIndex,
				sortBy);
	}

	public <T extends LDAPPERBean> PagedResultsForFilter<T> pagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, boolean returningObj, int contentsForPage,
			int startPageIndex, String[] sortBy) throws Exception {
		if (!isSupportVLVControl()) {
			throw new Exception(
					"Virtual List View Control not supported by LDAP Server in use!");
		}

		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return new PagedResultsForFilter<T>(tempCtx, startPointObj, objClass,
					filter, scope, targetAttributes, targetNestedObjs, returningObj,
					contentsForPage, startPageIndex, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		return sortedSearchEnumerationTA(startPointObj, objClass, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return sortedSearchEnumeration(tempCtx, startPointObj, objClass, filter,
					scope, targetAttributes, targetNestedObjs, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		return searchEnumerationTA(startPointObj, objClass, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return searchEnumeration(tempCtx, startPointObj, objClass, filter,
					scope, targetAttributes, targetNestedObjs);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> sortedSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		return sortedSearchTA(startPointObj, objClass, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	@SuppressWarnings("unchecked")
	public <T extends LDAPPERBean> ArrayList<T> sortedSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		try {
			resultEnumeration =
					sortedSearchEnumerationTA(startPointObj, objClass, filter,
							scope, targetAttributes, targetNestedObjs, sortBy);
			return (ArrayList<T>) resultEnumeration.getAllSearchResults();
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> search(LDAPPERBean startPointObj,
			Class<T> objClass, String filter, SearchScope scope,
			String[] targetAttributes, String[] targetNestedObjs) throws Exception {
		return searchTA(startPointObj, objClass, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	@SuppressWarnings("unchecked")
	public <T extends LDAPPERBean> ArrayList<T> searchTA(LDAPPERBean startPointObj,
			Class<T> objClass, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		try {
			resultEnumeration =
					searchEnumerationTA(startPointObj, objClass, filter, scope,
							targetAttributes, targetNestedObjs);
			return (ArrayList<T>) resultEnumeration.getAllSearchResults();
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}
		}
	}

	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedAttr)
			throws Exception {
		searchAndUpdateTA(startPointObj, obj, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, newObj, removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr) throws Exception {
		searchAndUpdateTA(startPointObj, obj, filter, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, newObj, removeUnspecifiedObj,
				removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedAttr) throws Exception {
		searchAndUpdateTA(startPointObj, obj, filter, scope, targetAttributes,
				targetNestedObjs, newObj, false, removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		LdapContext tempCtx = null;
		try {
			resultEnumeration =
					searchEnumerationTA(startPointObj, obj.getClass(), filter,
							scope, targetAttributes, targetNestedObjs);
			tempCtx = getTemporaryContext();

			updateAllSearchResults(tempCtx, resultEnumeration, newObj,
					removeUnspecifiedObj, removeUnspecifiedAttr);
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}

			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	/*
	 * Search methods with filterExpr
	 */

	public <T extends LDAPPERBean> SimplePagedResultsForFilterExpr<T> simplePagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String[] sortBy)
			throws Exception {
		return simplePagedResultsSearchTA(startPointObj, objClass, filterExpr,
				filterArgs, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, contentsForPage, sortBy);
	}

	public <T extends LDAPPERBean> SimplePagedResultsForFilterExpr<T> simplePagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception {
		if (!isSupportSimplePagedResultsControl()) {
			throw new Exception(
					"Simple Paged Results Control not supported by LDAP Server in use!");
		}

		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return new SimplePagedResultsForFilterExpr<T>(tempCtx, startPointObj,
					objClass, filterExpr, filterArgs, scope, targetAttributes,
					targetNestedObjs, contentsForPage, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> PagedResultsForFilterExpr<T> pagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, boolean returningObj, int contentsForPage,
			int startPageIndex, String[] sortBy) throws Exception {
		return pagedResultsSearchTA(startPointObj, objClass, filterExpr, filterArgs,
				scope, TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, returningObj, contentsForPage, startPageIndex,
				sortBy);
	}

	public <T extends LDAPPERBean> PagedResultsForFilterExpr<T> pagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returningObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception {
		if (!isSupportVLVControl()) {
			throw new Exception(
					"Virtual List View Control not supported by LDAP Server in use!");
		}

		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return new PagedResultsForFilterExpr<T>(tempCtx, startPointObj,
					objClass, filterExpr, filterArgs, scope, targetAttributes,
					targetNestedObjs, returningObj, contentsForPage, startPageIndex,
					sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception {
		return sortedSearchEnumerationTA(startPointObj, objClass, filterExpr,
				filterArgs, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return sortedSearchEnumeration(tempCtx, startPointObj, objClass,
					filterExpr, filterArgs, scope, targetAttributes,
					targetNestedObjs, sortBy);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		return searchEnumerationTA(startPointObj, objClass, filterExpr, filterArgs,
				scope, TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		LdapContext tempCtx = null;
		try {
			tempCtx = getTemporaryContext();

			return searchEnumeration(tempCtx, startPointObj, objClass, filterExpr,
					filterArgs, scope, targetAttributes, targetNestedObjs);
		}
		finally {
			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> sortedSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception {
		return sortedSearchTA(startPointObj, objClass, filterExpr, filterArgs,
				scope, TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, sortBy);
	}

	@SuppressWarnings("unchecked")
	public <T extends LDAPPERBean> ArrayList<T> sortedSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		try {
			resultEnumeration =
					sortedSearchEnumerationTA(startPointObj, objClass, filterExpr,
							filterArgs, scope, targetAttributes, targetNestedObjs,
							sortBy);
			return (ArrayList<T>) resultEnumeration.getAllSearchResults();
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}
		}
	}

	public <T extends LDAPPERBean> ArrayList<T> search(LDAPPERBean startPointObj,
			Class<T> objClass, String filterExpr, String[] filterArgs,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		return searchTA(startPointObj, objClass, filterExpr, filterArgs, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs);
	}

	@SuppressWarnings("unchecked")
	public <T extends LDAPPERBean> ArrayList<T> searchTA(LDAPPERBean startPointObj,
			Class<T> objClass, String filterExpr, String[] filterArgs,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		try {
			resultEnumeration =
					searchEnumerationTA(startPointObj, objClass, filterExpr,
							filterArgs, scope, targetAttributes, targetNestedObjs);
			return (ArrayList<T>) resultEnumeration.getAllSearchResults();
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}
		}
	}

	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedAttr) throws Exception {
		searchAndUpdateTA(startPointObj, obj, filterExpr, filterArgs, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, newObj, removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception {
		searchAndUpdateTA(startPointObj, obj, filterExpr, filterArgs, scope,
				TargetAttribute.getTargetAttributes(targetAttributes),
				targetNestedObjs, newObj, removeUnspecifiedObj,
				removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedAttr) throws Exception {
		searchAndUpdateTA(startPointObj, obj, filterExpr, filterArgs, scope,
				targetAttributes, targetNestedObjs, newObj, false,
				removeUnspecifiedAttr);
	}

	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception {
		SearchResultEnumeration resultEnumeration = null;
		LdapContext tempCtx = null;
		try {
			resultEnumeration =
					searchEnumerationTA(startPointObj, obj.getClass(), filterExpr,
							filterArgs, scope, targetAttributes, targetNestedObjs);

			tempCtx = getTemporaryContext();

			updateAllSearchResults(tempCtx, resultEnumeration, newObj,
					removeUnspecifiedObj, removeUnspecifiedAttr);
		}
		finally {
			if (resultEnumeration != null) {
				resultEnumeration.close();
			}

			if (tempCtx != null) {
				tempCtx.close();
			}
		}
	}

	/*
	 * Protected methods
	 */

	/**
	 * Add (for internal use)
	 */
	void add(LdapContext ctx, LDAPPERBean obj, String subContext,
			boolean enableAutomaticDataAttrs) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);

		if (enableAutomaticDataAttrs) {
			// Set all automatic data attributes
			eot.setAutomaticData(obj);
		}

		// Add object on LDAP server
		ctx.bind(subContext, eot.getObject(obj), eot.getAttributes(obj));

		EventType eventType = null;
		LDAPPEREventAdd event = null;
		if (eventGenerator != null) {
			// Send an event if a related subscription exists
			eventType = eventGenerator.getEventType(Operation.ADD, obj.getClass());
			if (eventType != EventType.NO_EVENT) {
				event =
						new LDAPPEREventAdd(eventType, getEventOriginatorName(ctx),
								false, eot.filterAttributes(obj));
				eventGenerator.sendAddEvent(event);
			}
		}

		if (eot.hasNestedObjs()) {
			LDAPPERBean[] nestedObjs;
			for (Iterator<NestedObjTemplate> iter = eot.getNestedObjIterator(); iter
					.hasNext();) {
				nestedObjs = iter.next().getNestedObj(obj);
				if (nestedObjs == null) {
					continue;
				}

				for (LDAPPERBean no : nestedObjs) {
					add(ctx, no, no.getDN(), enableAutomaticDataAttrs);
				}
			}
		}

		if (eventType != null && eventType.sendEventOnComplete()) {
			event.setCompleted(true);
			eventGenerator.sendAddEvent(event);
		}
	}

	/**
	 * Update (for internal use)
	 */
	boolean update(LdapContext ctx, LDAPPERBean obj, String subContext,
			boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr,
			boolean updateSubTree, boolean removeUnspecifiedNestedObj,
			boolean enableAutomaticDataAttrs) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);
		boolean modifiedObject = false;
		boolean modifiedAttrs = false;

		// Get current attributes and object on servers
		Attributes currAttrs;
		Object currObject;
		try {
			// Create SearchControls
			SearchControls searchControls = new SearchControls();
			searchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
			searchControls.setReturningAttributes(null);
			searchControls.setReturningObjFlag(true);

			SearchResult res =
					ctx.search(subContext, eot.addObjClassesToSearchFilter(null),
							searchControls).next();
			currAttrs = res.getAttributes();
			currObject = res.getObject();
		}
		catch (Exception e) {
			return false;
		}

		Object object = eot.getObject(obj);
		if (object != null || removeUnspecifiedObj && currObject != null) {
			// Set object
			ctx.rebind(subContext, object);
			modifiedObject = true;
		}

		if (enableAutomaticDataAttrs) {
			// Set all automatic data attributes
			eot.setAutomaticData(obj);
		}

		// Get ModificationItems attrs
		List<ModificationItem> mItems =
				eot.getModificationItem(obj, removeUnspecifiedAttr, currAttrs);

		// Remove userPassword attribute ModificationItem if any
		for (Iterator<ModificationItem> iterator = mItems.iterator(); iterator
				.hasNext();) {
			if (iterator.next().getAttribute().getID().equals("userPassword")) {
				iterator.remove();
				break;
			}
		}

		if (mItems.size() > 0) {
			// Update attributes
			ctx.modifyAttributes(subContext,
					mItems.toArray(new ModificationItem[mItems.size()]));
			modifiedAttrs = true;
		}

		EventType eventType = null;
		LDAPPEREventUpdate event = null;
		if ((modifiedObject || modifiedAttrs) && eventGenerator != null) {
			// Send an event if a related subscription exists
			eventType =
					eventGenerator.getEventType(Operation.UPDATE, obj.getClass());
			if (eventType != EventType.NO_EVENT) {
				// Create beans for event
				LDAPPERBean fullBean = eot.getExternalObjNewInstance();
				LDAPPERBean addedDeltaBean = eot.getExternalObjNewInstance();
				LDAPPERBean removedDeltaBean = eot.getExternalObjNewInstance();

				// Set DN
				fullBean.setDN(subContext);
				addedDeltaBean.setDN(subContext);
				removedDeltaBean.setDN(subContext);

				// Set object
				if (modifiedObject) {
					eot.setObject(removedDeltaBean, currObject);
					if (object != null) {
						eot.setObject(fullBean, object);
						eot.setObject(addedDeltaBean, object);
					}
				} else if (currObject != null) {
					eot.setObject(fullBean, currObject);
				}

				// Set attributes
				Attributes addedDeltaAttrs = new BasicAttributes();
				Attributes removedDeltaAttrs = new BasicAttributes();
				Attribute attr;
				int modOp;
				for (ModificationItem mItem : mItems) {
					attr = mItem.getAttribute();
					modOp = mItem.getModificationOp();
					if (modOp == DirContext.REMOVE_ATTRIBUTE) {
						removedDeltaAttrs.put(attr);
						currAttrs.remove(attr.getID());
					} else {
						if (modOp == DirContext.REPLACE_ATTRIBUTE) {
							Attribute remAttr = currAttrs.remove(attr.getID());
							Attribute addAttr = new BasicAttribute(attr.getID());
							Object value;
							for (NamingEnumeration<?> e = attr.getAll(); e.hasMore();) {
								value = e.next();
								if (!remAttr.remove(value)) {
									addAttr.add(value);
								}
							}
							if (remAttr.size() > 0) {
								removedDeltaAttrs.put(remAttr);
							}
							if (addAttr.size() > 0) {
								addedDeltaAttrs.put(addAttr);
							}
						} else {
							// modOp == DirContext.ADD_ATTRIBUTE
							addedDeltaAttrs.put(attr);
						}
						currAttrs.put(attr);
					}
				}
				eot.setAttributes(fullBean, currAttrs, null, false);
				if (modifiedAttrs) {
					eot.setAttributes(addedDeltaBean, addedDeltaAttrs, null, false);
					eot.setAttributes(removedDeltaBean, removedDeltaAttrs, null,
							false);
				}

				// Send event
				event =
						new LDAPPEREventUpdate(eventType,
								getEventOriginatorName(ctx), false, fullBean,
								addedDeltaBean, removedDeltaBean);
				eventGenerator.sendUpdateEvent(event);
			}
		}

		// Update nested objects
		if (updateSubTree && eot.hasNestedObjs()) {
			NestedObjTemplate not;
			LDAPPERBean[] nestedObjs;
			for (Iterator<NestedObjTemplate> iter = eot.getNestedObjIterator(); iter
					.hasNext();) {
				not = iter.next();
				nestedObjs = not.getNestedObj(obj);
				if (nestedObjs == null) {
					continue;
				}

				String nestedSubContext;
				for (LDAPPERBean no : nestedObjs) {
					nestedSubContext = no.getDN();
					if (!update(ctx, no, nestedSubContext, removeUnspecifiedObj,
							removeUnspecifiedAttr, true, removeUnspecifiedNestedObj,
							enableAutomaticDataAttrs)) {
						add(ctx, no, nestedSubContext, enableAutomaticDataAttrs);
					}
				}

				if (removeUnspecifiedNestedObj || nestedObjs.length == 0) {
					ExternalObjTemplate notEot = not.getExternalObjTemplate();

					// Create filter to exclude updated nestedObjs
					String filter = null;
					if (nestedObjs.length > 0) {
						StringBuilder sbFilter = new StringBuilder();
						sbFilter.append("(!(|");
						for (LDAPPERBean no : nestedObjs) {
							sbFilter.append('(').append(notEot.getLDAPObjectId(no))
									.append(')');
						}
						sbFilter.append("))");
						filter = sbFilter.toString();
					}

					// Search nested objects to remove
					SearchControls searchControls = new SearchControls();
					searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
					searchControls
							.setReturningAttributes(LDAPPERBean.EmptyTargetAttributes);
					NamingEnumeration<SearchResult> res =
							ctx.search(subContext,
									notEot.addObjClassesToSearchFilter(filter),
									searchControls);

					if (res.hasMoreElements()) {
						// Create new instance of nested object
						LDAPPERBean nestedObjToRemove =
								not.getExternalObjNewInstance();

						while (res.hasMoreElements()) {
							// Set DN
							nestedObjToRemove.setDN(res.nextElement()
									.getNameInNamespace());
							// Remove nested object
							remove(ctx, nestedObjToRemove, false);
						}
					}
				}
			}
		}

		if (eventType != null && eventType.sendEventOnComplete()) {
			event.setCompleted(true);
			eventGenerator.sendUpdateEvent(event);
		}

		return true;
	}

	/**
	 * Remove all (for internal use)
	 */
	void remove(LdapContext ctx, LDAPPERBean obj, boolean isInTxn) throws Exception {
		Queue<LDAPPEREventRemove> removeEventStack = null;

		if (isSupportTreeDeleteControl()) {
			// Remove directly target context
			if (eventGenerator != null) {
				removeEventStack = getRemoveEventStack(ctx, obj);
			}

			LDAPUtilities.addRequestControlToContext(ctx, new TreeDeleteControl());

			try {
				ctx.unbind(obj.getDN());
				if (removeEventStack != null) {
					LDAPPEREventRemove event;
					EventType eventType;
					while ((event = removeEventStack.poll()) != null) {
						eventType = event.getEventType();
						if (eventType != null && eventType != EventType.NO_EVENT) {
							if (event.isCompleted()
									&& !eventType.sendEventOnComplete()) {
								continue;
							}
							eventGenerator.sendRemoveEvent(event);
						}
					}
				}
				removeEventStack = null;
				return;
			}
			catch (ContextNotEmptyException e) {
				if (isInTxn) {
					// Propago il fallimento in caso di transazione attiva
					throw e;
				}
				supportTreeDeleteControl = false;
			}
		}

		// Remove recursively target context
		if (removeEventStack == null) {
			removeEventStack = getRemoveEventStack(ctx, obj);
		}

		LDAPPEREventRemove event;
		EventType eventType;
		while ((event = removeEventStack.poll()) != null) {
			if (event.isCompleted()) {
				ctx.unbind(event.getRemovedObj().getDN());
			}

			if (eventGenerator != null) {
				eventType = event.getEventType();
				if (eventType != null && eventType != EventType.NO_EVENT) {
					if (event.isCompleted() && !eventType.sendEventOnComplete()) {
						continue;
					}
					eventGenerator.sendRemoveEvent(event);
				}
			}
		}
		removeEventStack = null;
	}

	/**
	 * Fetch (for internal use)
	 */
	void fetch(LdapContext ctx, LDAPPERBean obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		SearchResultEnumeration sre = null;
		try {
			sre =
					SearchResultEnumeration.searchEnumeration(ctx, obj.getDN(),
							obj.getClass(), null, SearchScope.OBJECT_SCOPE,
							targetAttributes, targetNestedObjs);
			if (!sre.hasMoreElements()) {
				throw new NameNotFoundException("Object '" + obj.getDN()
						+ "' not found!");
			}
			sre.nextElement(obj);
		}
		finally {
			if (sre != null) {
				sre.close();
			}
		}
	}

	/**
	 * List (for internal use)
	 */
	@SuppressWarnings("unchecked")
	<T extends LDAPPERBean> ArrayList<T> list(LdapContext ctx, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		SearchResultEnumeration sre = null;
		try {
			sre =
					SearchResultEnumeration.searchEnumeration(ctx, obj.getBaseDN(),
							obj, targetAttributes, targetNestedObjs);
			return (ArrayList<T>) sre.getAllSearchResults();
		}
		finally {
			if (sre != null) {
				sre.close();
			}
		}
	}

	/**
	 * Sorted list (for internal use)
	 */
	<T extends LDAPPERBean> ArrayList<T> sortedList(LdapContext ctx, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		if (!isSupportSortControl()) {
			throw new Exception("Sort Control not supported by LDAP Server in use!");
		}

		// Create and add SortControl to context
		SortControl sc =
				LDAPUtilities.getSortControl(ExternalObjTemplate.getInstance(obj),
						sortBy);
		LDAPUtilities.addRequestControlToContext(ctx, sc);

		return list(ctx, obj, targetAttributes, targetNestedObjs);
	}

	/**
	 * Sorted searchEnumeration with matching bean attributes (for internal use)
	 */
	<T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LdapContext ctx, LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		if (!isSupportSortControl()) {
			throw new Exception("Sort Control not supported by LDAP Server in use!");
		}

		// Create and add SortControl to context
		SortControl sc =
				LDAPUtilities.getSortControl(ExternalObjTemplate.getInstance(obj),
						sortBy);
		LDAPUtilities.addRequestControlToContext(ctx, sc);

		return searchEnumeration(ctx, startPointObj, obj, targetAttributes,
				targetNestedObjs);
	}

	/**
	 * Sorted searchEnumeration with filter (for internal use)
	 */
	<T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LdapContext ctx, LDAPPERBean startPointObj, Class<T> objClass,
			String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		if (!isSupportSortControl()) {
			throw new Exception("Sort Control not supported by LDAP Server in use!");
		}

		// Create and add SortControl to context
		SortControl sc =
				LDAPUtilities.getSortControl(
						ExternalObjTemplate.getInstance(objClass), sortBy);
		LDAPUtilities.addRequestControlToContext(ctx, sc);

		return searchEnumeration(ctx, startPointObj, objClass, filter, scope,
				targetAttributes, targetNestedObjs);
	}

	/**
	 * Sorted searchEnumeration with filterExpr (for internal use)
	 */
	<T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LdapContext ctx, LDAPPERBean startPointObj, Class<T> objClass,
			String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception {
		if (!isSupportSortControl()) {
			throw new Exception("Sort Control not supported by LDAP Server in use!");
		}

		// Create and add SortControl to context
		SortControl sc =
				LDAPUtilities.getSortControl(
						ExternalObjTemplate.getInstance(objClass), sortBy);
		LDAPUtilities.addRequestControlToContext(ctx, sc);

		return searchEnumeration(ctx, startPointObj, objClass, filterExpr,
				filterArgs, scope, targetAttributes, targetNestedObjs);
	}

	/**
	 * Search enumeration with matching bean attributes (for internal use)
	 */
	<T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LdapContext ctx, LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		String startPointDN;
		if (startPointObj != null) {
			startPointDN = startPointObj.getDN();
		} else {
			startPointDN = "";
		}

		return SearchResultEnumeration.searchEnumeration(ctx, startPointDN, obj,
				targetAttributes, targetNestedObjs);
	}

	/**
	 * Search enumeration with filter (for internal use)
	 */
	<T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LdapContext ctx, LDAPPERBean startPointObj, Class<T> objClass,
			String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		String startPointDN;
		if (startPointObj != null) {
			startPointDN = startPointObj.getDN();
		} else {
			startPointDN = "";
		}

		return SearchResultEnumeration.searchEnumeration(ctx, startPointDN,
				objClass, filter, scope, targetAttributes, targetNestedObjs);
	}

	/**
	 * Search enumeration with filterExpr (for internal use)
	 */
	<T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LdapContext ctx, LDAPPERBean startPointObj, Class<T> objClass,
			String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception {
		String startPointDN;
		if (startPointObj != null) {
			startPointDN = startPointObj.getDN();
		} else {
			startPointDN = "";
		}

		return SearchResultEnumeration.searchEnumeration(ctx, startPointDN,
				objClass, filterExpr, filterArgs, scope, targetAttributes,
				targetNestedObjs);
	}

	/**
	 * Populate argument stack with all subtree DNs (for internal use)
	 */
	private void loadRemoveEventStack(Queue<LDAPPEREventRemove> stack,
			LDAPPERBean obj, String originatorId) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);

		EventType eventType = null;
		LDAPPERBean filteredBean = null;
		if (eventGenerator != null) {
			eventType =
					eventGenerator.getEventType(Operation.REMOVE,
							eot.getExternalObjClass());
			if (eventType != null && eventType != EventType.NO_EVENT) {
				filteredBean = eot.filterAttributes(obj);
			}
		}
		stack.add(new LDAPPEREventRemove(eventType, originatorId, true, filteredBean));

		if (eot.hasNestedObjs()) {
			LDAPPERBean[] nestedObjs;
			for (Iterator<NestedObjTemplate> iter = eot.getNestedObjIterator(); iter
					.hasNext();) {
				nestedObjs = iter.next().getNestedObj(obj);
				if (nestedObjs == null) {
					continue;
				}

				for (LDAPPERBean no : nestedObjs) {
					loadRemoveEventStack(stack, no, originatorId);
				}
			}
		}

		if (filteredBean != null) {
			stack.add(new LDAPPEREventRemove(eventType, originatorId, false,
					filteredBean));
		}
	}

	/**
	 * Return a stack with all object subtree (for internal use)
	 */
	private Queue<LDAPPEREventRemove> getRemoveEventStack(LdapContext ctx,
			LDAPPERBean obj) throws Exception {
		Queue<LDAPPEREventRemove> remEventStack =
				Collections.asLifoQueue(new ArrayDeque<LDAPPEREventRemove>(10));
		LDAPPERBean tmpObj = ExternalObjTemplate.getInstance(obj).filterDN(obj);
		fetch(ctx, tmpObj, null, null);
		loadRemoveEventStack(remEventStack, tmpObj, getEventOriginatorName(ctx));
		return remEventStack;
	}

	/**
	 * Update all search results (for internal use)
	 */
	void updateAllSearchResults(LdapContext ctx, SearchResultEnumeration searchRes,
			LDAPPERBean newObj, boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr) throws Exception {
		LDAPPERBean searchResultObj;
		while (searchRes.hasMoreElements()) {
			searchResultObj = searchRes.nextElement();
			// TODO Abilitati gli automatic data attributes di default
			// per non dover duplicare le firme dei metodi che chiamano questo
			// metodo
			// (In considerazione del fatto che sono pochissimi ad usare questa
			// funzionalità)
			update(ctx, newObj, searchResultObj.getDN(), removeUnspecifiedObj,
					removeUnspecifiedAttr, true, true, true);
		}
	}

	/**
	 * Check on DN nested object's consistency
	 */
	void dnCheck(LDAPPERBean obj, String objDN) throws Exception {
		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(obj);

		if (eot.hasNestedObjs()) {
			LDAPPERBean[] nestedObjs;
			for (Iterator<NestedObjTemplate> iter = eot.getNestedObjIterator(); iter
					.hasNext();) {
				nestedObjs = iter.next().getNestedObj(obj);
				if (nestedObjs == null) {
					continue;
				}

				String noDN;
				for (LDAPPERBean no : nestedObjs) {
					noDN = no.getDN();
					if (!noDN.endsWith(objDN)) {
						throw new InvalidDN(noDN,
								"A nested object has a invalid DN '" + noDN
										+ "', correct DN should terminate in '"
										+ objDN + "'");
					}
					dnCheck(no, noDN);
				}
			}
		}
	}

	/**
	 * Retrieves if this LDAPPER context supports extension with OID equals to
	 * extensionOID
	 */
	public boolean hasExtensionSupport(String extensionOID) {
		if (supportedExtensions == null) {
			try {
				String username = (String) env.get(Context.SECURITY_PRINCIPAL);
				String password = (String) env.get(Context.SECURITY_CREDENTIALS);
				String auth = (String) env.get(Context.SECURITY_AUTHENTICATION);
				String url = (String) env.get(Context.PROVIDER_URL);
				supportedExtensions =
						LDAPUtilities.getSupportedExtensions(username, password,
								url, auth);
			}
			catch (NamingException e) {
				log.error("Error retriving supported extensions from LDAP Server. Use default supported extensions");
				supportedExtensions = DEFAULT_SUPPORTED_EXTENDIONS;
			}
		}
		return supportedExtensions.contains(extensionOID);
	}

	/**
	 * Retrieves if this LDAPPER context supports control with OID equals to
	 * controlOID
	 */
	public boolean hasControlSupport(String controlOID) {
		if (supportedControls == null) {
			try {
				String username = (String) env.get(Context.SECURITY_PRINCIPAL);
				String password = (String) env.get(Context.SECURITY_CREDENTIALS);
				String auth = (String) env.get(Context.SECURITY_AUTHENTICATION);
				String url = (String) env.get(Context.PROVIDER_URL);
				supportedControls =
						LDAPUtilities.getSupportedControls(username, password, url,
								auth);
			}
			catch (NamingException e) {
				log.error("Error retriving supported controls from LDAP Server. Use default supported controls");
				supportedControls = DEFAULT_SUPPORTED_CONTROLS;
			}
		}
		return supportedControls.contains(controlOID);
	}

	private String getEventOriginatorName(LdapContext ctx) throws Exception {
		Control control =
				LDAPUtilities.getCtxRequestControl(ctx,
						ProxyAuthorizationControl.OID_PROXYAUTH_REQUEST_CONTROL);

		if (control != null) {
			return ((ProxyAuthorizationControl) control).getAuthIdDN();
		} else {
			return (String) ctx.getEnvironment().get(Context.SECURITY_PRINCIPAL);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		env = (Properties) in.readObject();
		eventGenerator = (LDAPPEREventGenerator) in.readObject();
		supportedControls = (Attribute) in.readObject();
		supportedExtensions = (Attribute) in.readObject();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(env);
		out.writeObject(eventGenerator);
		out.writeObject(supportedControls);
		out.writeObject(supportedExtensions);
	}
}
