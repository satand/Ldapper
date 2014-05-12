package com.ldapper.context;

import java.util.ArrayList;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.LdapContext;

import com.ldapper.context.search.PagedResultsForAttributes;
import com.ldapper.context.search.PagedResultsForFilter;
import com.ldapper.context.search.PagedResultsForFilterExpr;
import com.ldapper.context.search.SearchResultEnumeration;
import com.ldapper.context.search.SearchScope;
import com.ldapper.context.search.SimplePagedResultsForAttributes;
import com.ldapper.context.search.SimplePagedResultsForFilter;
import com.ldapper.context.search.SimplePagedResultsForFilterExpr;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;

public interface ILDAPPERContext {

	public static final String PROP_CONTEXT_FACTORY =
			Context.INITIAL_CONTEXT_FACTORY;
	public static final String PROP_CONNECTION_TIMEOUT =
			"com.sun.jndi.ldap.connect.timeout";
	public static final String PROP_SECURITY_AUTHENTICATION =
			Context.SECURITY_AUTHENTICATION;
	public static final String PROP_BATCHSIZE = Context.BATCHSIZE;
	public static final String PROP_CONTEXT_REFERRAL = Context.REFERRAL;
	public static final String PROP_SUPPORTED_RENAME_SUBTREE =
			"com.ldapper.context.supportedRenameSubtree";
	public static final String PROP_CONTROL_FACTORIES =
			LdapContext.CONTROL_FACTORIES;
	public static final String PROP_EVENT_GENERATOR =
			"com.ldapper.context.eventGenerator";
	public static final String PROP_EVENT_LISTENER =
			"com.ldapper.context.eventListener";
	public static final String PROP_SUPPORTED_EXTENSIONS =
			"com.ldapper.context.supportedExtensions";
	public static final String PROP_SUPPORTED_CONTROLS =
			"com.ldapper.context.supportedControls";

	public static final String DEFAULT_CONTEXT_FACTORY =
			"com.sun.jndi.ldap.LdapCtxFactory";
	public static final String DEFAULT_AUTHENTICATION = "simple";
	public static final String DEFAULT_TIMEOUT = "1000";
	public static final String DEFAULT_BATCHSIZE = "21";
	public static final String DEFAULT_CONTEXT_REFERRAL = "ignore";
	public static final String DEFAULT_SUPPORTED_RENAME_SUBTREE = "true";
	public static final String DEFAULT_CONTROL_FACTORIES =
			"com.ldapper.context.control.ResponseControlFactory";
	public static final Attribute DEFAULT_SUPPORTED_CONTROLS = new BasicAttribute(
			"supportedControl");
	public static final Attribute DEFAULT_SUPPORTED_EXTENDIONS = new BasicAttribute(
			"supportedExtension");

	public String getAuthIdentityDN();

	public Properties getEnvironment();

	public boolean checkAuthentication() throws Exception;

	/**
	 * Performs an extended operation.
	 * 
	 * This method is used to support LDAPv3 extended operations.
	 * 
	 * @param request
	 *            The non-null request to be performed.
	 * @return The possibly null response of the operation. null means the
	 *         operation did not generate any response.
	 * @throws Exception
	 *             If an error occurred while performing the extended operation.
	 */
	public ExtendedResponse extendedOperation(ExtendedRequest request)
			throws Exception;

	/**
	 * Change password
	 */
	public String changeUserPassword(LDAPPERBean user, String oldPassword,
			String newPassword) throws Exception;

	/**
	 * Exist
	 */
	public boolean exists(LDAPPERBean obj) throws Exception;

	/**
	 * Check if an object is a leaf into tree
	 */
	public boolean isLeaf(LDAPPERBean obj) throws Exception;

	/**
	 * Add
	 */
	public void add(LDAPPERBean obj) throws Exception;

	/**
	 * Add
	 */
	public void add(LDAPPERBean obj, boolean enableAutomaticDataAttrs)
			throws Exception;

	/**
	 * Update - Add missing objects and update preexisting objects attributes
	 * with the exception of userPassword that goes unheeded
	 */
	public void update(LDAPPERBean obj, boolean removeUnspecifiedAttr,
			boolean updateSubTree, boolean removeUnspecifiedNestedObj)
			throws Exception;

	/**
	 * Update - Add missing objects and update preexisting objects attributes
	 * with the exception of userPassword that goes unheeded
	 */
	public void update(LDAPPERBean obj, boolean removeUnspecifiedAttr,
			boolean updateSubTree, boolean removeUnspecifiedNestedObj,
			boolean enableAutomaticDataAttrs) throws Exception;

	/**
	 * Update - Add missing objects and update preexisting objects attributes
	 * with the exception of userPassword that goes unheeded
	 */
	public void update(LDAPPERBean obj, Boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr, boolean updateSubTree,
			boolean removeUnspecifiedNestedObj) throws Exception;

	/**
	 * Update - Add missing objects and update preexisting objects attributes
	 * with the exception of userPassword that goes unheeded
	 */
	public void update(LDAPPERBean obj, Boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr, boolean updateSubTree,
			boolean removeUnspecifiedNestedObj, boolean enableAutomaticDataAttrs)
			throws Exception;

	/**
	 * Move
	 */
	public void move(LDAPPERBean origObj, LDAPPERBean destSubContextObj)
			throws Exception;

	/**
	 * Move
	 */
	public void move(LDAPPERBean origObj, LDAPPERBean destSubContextObj,
			boolean enableAutomaticDataAttrs) throws Exception;

	/**
	 * Remove all
	 */
	public void remove(LDAPPERBean obj) throws Exception;

	/**
	 * Count of objects with obj attributes
	 */
	public int count(LDAPPERBean obj) throws Exception;

	/**
	 * List with all target attributes and including subtree
	 */
	public <T extends LDAPPERBean> ArrayList<T> list(T obj) throws Exception;

	/**
	 * List
	 */
	public <T extends LDAPPERBean> ArrayList<T> list(T obj,
			String[] targetAttributes, String[] targetNestedObjs) throws Exception;

	/**
	 * List with target attributes
	 */
	public <T extends LDAPPERBean> ArrayList<T> listTA(T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Sorted list with all target attributes and including subtree
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedList(T obj, String[] sortBy)
			throws Exception;

	/**
	 * Sorted list
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedList(T obj,
			String[] targetAttributes, String[] targetNestedObjs, String[] sortBy)
			throws Exception;

	/**
	 * Sorted list with target attributes
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedListTA(T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Fetch with all target attributes and including subtree
	 */
	public void fetch(LDAPPERBean obj) throws Exception;

	/**
	 *  
	 */
	public void fetch(LDAPPERBean obj, String[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/**
	 * Fetch with target attributes
	 */
	public void fetchTA(LDAPPERBean obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/*
	 * Search methods with matching bean attributes
	 */

	/**
	 * Search (using matching bean attributes) with simple paged results
	 */
	public <T extends LDAPPERBean> SimplePagedResultsForAttributes<T> simplePagedResultsSearch(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String[] sortBy)
			throws Exception;

	/**
	 * Search (using matching bean attributes) with simple paged results
	 */
	public <T extends LDAPPERBean> SimplePagedResultsForAttributes<T> simplePagedResultsSearchTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception;

	/**
	 * Search (using matching bean attributes) with paged results
	 */
	public <T extends LDAPPERBean> PagedResultsForAttributes<T> pagedResultsSearch(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, boolean returnObj, int contentsForPage,
			int startPageIndex, String[] sortBy) throws Exception;

	/**
	 * Search (using matching bean attributes) with paged results
	 */
	public <T extends LDAPPERBean> PagedResultsForAttributes<T> pagedResultsSearchTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returnObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception;

	/**
	 * Sorted searchEnumeration (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception;

	/**
	 * Sorted searchEnumeration (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumerationTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Search enumeration (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/**
	 * Search enumeration (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumerationTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Sorted search (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedSearch(
			LDAPPERBean startPointObj, T obj, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception;

	/**
	 * Sorted search (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedSearchTA(
			LDAPPERBean startPointObj, T obj,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Search (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> ArrayList<T> search(LDAPPERBean startPointObj,
			T obj, String[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Search (using matching bean attributes)
	 */
	public <T extends LDAPPERBean> ArrayList<T> searchTA(LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/**
	 * Search (using matching bean attributes) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedAttr) throws Exception;

	/**
	 * Search (using matching bean attributes) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception;

	/**
	 * Search (using matching bean attributes) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedAttr)
			throws Exception;

	/**
	 * Search (using matching bean attributes) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr) throws Exception;

	/*
	 * Search methods with filter
	 */

	/**
	 * Search (using filter) with simple paged results
	 */
	public <T extends LDAPPERBean> SimplePagedResultsForFilter<T> simplePagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception;

	/**
	 * Search (using filter) with simple paged results
	 */
	public <T extends LDAPPERBean> SimplePagedResultsForFilter<T> simplePagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String[] sortBy)
			throws Exception;

	/**
	 * Search (using filter) with paged results
	 */
	public <T extends LDAPPERBean> PagedResultsForFilter<T> pagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			boolean returningObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception;

	/**
	 * Search (using filter) with paged results
	 */
	public <T extends LDAPPERBean> PagedResultsForFilter<T> pagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, boolean returningObj, int contentsForPage,
			int startPageIndex, String[] sortBy) throws Exception;

	/**
	 * Sorted searchEnumeration (using filter)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Sorted searchEnumeration (using filter)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception;

	/**
	 * Search enumeration (using filter)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Search enumeration (using filter)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/**
	 * Sorted search (using filter)
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Sorted search (using filter)
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filter,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception;

	/**
	 * Search (using filter)
	 */
	public <T extends LDAPPERBean> ArrayList<T> search(LDAPPERBean startPointObj,
			Class<T> objClass, String filter, SearchScope scope,
			String[] targetAttributes, String[] targetNestedObjs) throws Exception;

	/**
	 * Search (using filter)
	 */
	public <T extends LDAPPERBean> ArrayList<T> searchTA(LDAPPERBean startPointObj,
			Class<T> objClass, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Search (using filter) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedAttr)
			throws Exception;

	/**
	 * Search (using filter) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, T newObj, boolean removeUnspecifiedObj,
			boolean removeUnspecifiedAttr) throws Exception;

	/**
	 * Search (using filter) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedAttr) throws Exception;

	/**
	 * Search (using filter) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filter, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception;

	/*
	 * Search methods with filterExpr
	 */

	/**
	 * Search (using filterExpr) with simple paged results
	 */
	public <T extends LDAPPERBean> SimplePagedResultsForFilterExpr<T> simplePagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String[] sortBy)
			throws Exception;

	/**
	 * Search (using filterExpr) with simple paged results
	 */
	public <T extends LDAPPERBean> SimplePagedResultsForFilterExpr<T> simplePagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			int contentsForPage, String[] sortBy) throws Exception;

	/**
	 * Search (using filterExpr) with paged results
	 */
	public <T extends LDAPPERBean> PagedResultsForFilterExpr<T> pagedResultsSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, boolean returningObj, int contentsForPage,
			int startPageIndex, String[] sortBy) throws Exception;

	/**
	 * Search (using filterExpr) with paged results
	 */
	public <T extends LDAPPERBean> PagedResultsForFilterExpr<T> pagedResultsSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			boolean returningObj, int contentsForPage, int startPageIndex,
			String[] sortBy) throws Exception;

	/**
	 * Sorted searchEnumeration (using filterExpr)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception;

	/**
	 * Sorted searchEnumeration (using filterExpr)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration sortedSearchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Search enumeration (using filterExpr)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumeration(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/**
	 * Search enumeration (using filterExpr)
	 */
	public <T extends LDAPPERBean> SearchResultEnumeration searchEnumerationTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Sorted search (using filterExpr)
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedSearch(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope, String[] targetAttributes,
			String[] targetNestedObjs, String[] sortBy) throws Exception;

	/**
	 * Sorted search (using filterExpr)
	 */
	public <T extends LDAPPERBean> ArrayList<T> sortedSearchTA(
			LDAPPERBean startPointObj, Class<T> objClass, String filterExpr,
			String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			String[] sortBy) throws Exception;

	/**
	 * Search (using filterExpr)
	 */
	public <T extends LDAPPERBean> ArrayList<T> search(LDAPPERBean startPointObj,
			Class<T> objClass, String filterExpr, String[] filterArgs,
			SearchScope scope, String[] targetAttributes, String[] targetNestedObjs)
			throws Exception;

	/**
	 * Search (using filterExpr)
	 */
	public <T extends LDAPPERBean> ArrayList<T> searchTA(LDAPPERBean startPointObj,
			Class<T> objClass, String filterExpr, String[] filterArgs,
			SearchScope scope, LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs) throws Exception;

	/**
	 * Search (using filterExpr) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedAttr) throws Exception;

	/**
	 * Search (using filterExpr) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdate(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			String[] targetAttributes, String[] targetNestedObjs, T newObj,
			boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception;

	/**
	 * Search (using filterExpr) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedAttr) throws Exception;

	/**
	 * Search (using filterExpr) and update
	 */
	public <T extends LDAPPERBean> void searchAndUpdateTA(LDAPPERBean startPointObj,
			T obj, String filterExpr, String[] filterArgs, SearchScope scope,
			LDAPPERTargetAttribute[] targetAttributes, String[] targetNestedObjs,
			T newObj, boolean removeUnspecifiedObj, boolean removeUnspecifiedAttr)
			throws Exception;

}
