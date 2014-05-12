package com.ldapper.context.search;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortResponseControl;

import org.apache.log4j.Logger;

import com.ldapper.context.LDAPPERContext;
import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;
import com.ldapper.data.extension.DataSerializableAsString;
import com.ldapper.utility.LDAPUtilities;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

public abstract class SimplePagedResults<T extends LDAPPERBean> implements
		Serializable, DataSerializableAsString {

	private static final long serialVersionUID = -7993581054599571646L;
	private static final Logger log = Logger
			.getLogger(SimplePagedResults.class);

	LdapContext ctx;
	String startPointDN;
	Class<? extends LDAPPERBean> objClass;
	String[] attrsToReturn;

	int contentsForPage;
	LDAPPERTargetAttribute[] targetAttributes;
	String[] targetNestedObjs;
	private int contentCount = 0;
	byte[] cookie;
	private int currentPage = 0;

	// Result cache
	private transient HashMap<Integer, ArrayList<?>> cache = new HashMap<Integer, ArrayList<?>>();

	private transient Object lock = new Object();

	void init(LdapContext context, LDAPPERBean startPointObj,
			Class<? extends LDAPPERBean> objClass,
			LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, int contentsForPage, String... sortBy)
			throws Exception {
		this.ctx = context.newInstance(context.getRequestControls());
		this.objClass = objClass;
		this.targetAttributes = targetAttributes;
		this.targetNestedObjs = targetNestedObjs;

		if (contentsForPage < 1) {
			throw new Exception("Contents for page must be > 0");
		}
		int maxContentsForPage = Integer.parseInt((String) context
				.getEnvironment().get(LDAPPERContext.PROP_BATCHSIZE)) - 1;
		if (contentsForPage > maxContentsForPage) {
			throw new Exception("Contents for page must be <= "
					+ maxContentsForPage);
		}
		this.contentsForPage = contentsForPage;

		if (startPointObj != null) {
			startPointDN = startPointObj.getDN();
		} else {
			startPointDN = "";
		}

		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(objClass);
		// Get attributes to return
		List<String> attrsToReturnList = eot
				.checkTargetAttributes(targetAttributes);
		attrsToReturn = attrsToReturnList == null ? null : attrsToReturnList
				.toArray(new String[attrsToReturnList.size()]);

		// Create mandatory SortControl and add this to context
		SortControl sortControl = LDAPUtilities.getSortControl(eot, sortBy);
		LDAPUtilities.addRequestControlToContext(ctx, sortControl);
	}

	public int getContectsForPage() {
		return contentsForPage;
	}

	public boolean isKnownContectCount() {
		return contentCount != 0;
	}

	public boolean hasNextPage() {
		return cookie != null;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public int getContentCount() {
		return contentCount;
	}

	public int getPageCount() {
		if (contentCount != 0) {
			int res = contentCount / contentsForPage;
			if (contentCount % contentsForPage != 0) {
				res += 1;
			}
			return res;
		}
		return 0;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<T> getPageResults(int pageIndex) throws Exception {
		if (pageIndex < 1) {
			throw new Exception("Invalid page index. It must be > 0");
		} else if (contentCount != 0 && pageIndex > 1
				&& pageIndex > getPageCount()) {
			throw new Exception("Invalid page index. It must be > 0 and < "
					+ getPageCount());
		}

		synchronized (lock) {
			ArrayList<T> res = (ArrayList<T>) cache.get(pageIndex);
			if (res == null) {
				while (currentPage < pageIndex) {

					SearchResultEnumeration sre = null;
					try {
						sre = getNextPageResultsEnumeration();

						res = (ArrayList<T>) sre.getAllSearchResults();
						currentPage++;
						cache.put(currentPage, res);
					} finally {
						if (sre != null) {
							sre.close();
						}
					}
				}
			}
			return res;
		}
	}

	@SuppressWarnings("unchecked")
	public ArrayList<T> getNextPage() throws Exception {
		synchronized (lock) {
			if (currentPage == 0) {
				return (ArrayList<T>) cache.get(++currentPage);
			}

			SearchResultEnumeration sre = null;
			try {
				sre = getNextPageResultsEnumeration();

				ArrayList<T> res = (ArrayList<T>) sre.getAllSearchResults();
				currentPage++;
				cache.put(currentPage, res);
				return res;
			} finally {
				if (sre != null) {
					sre.close();
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	void initializeContentsCount() throws Exception {
		synchronized (lock) {
			SearchResultEnumeration sre = null;
			try {
				sre = getNextPageResultsEnumeration();

				ArrayList<T> res = (ArrayList<T>) sre.getAllSearchResults();
				cache.put(1, res);
			} finally {
				if (sre != null) {
					sre.close();
				}
			}
		}
	}

	private SearchResultEnumeration getNextPageResultsEnumeration()
			throws Exception {
		// Create PagedResultsControl and add this to context
		PagedResultsControl pagedResultsControl = new PagedResultsControl(
				contentsForPage, cookie, true);
		LDAPUtilities.addRequestControlToContext(ctx, pagedResultsControl);

		try {
			NamingEnumeration<SearchResult> searchRes = search();

			if (searchRes.hasMoreElements()) {
				// Examine the paged results control response
				Control[] controls = ctx.getResponseControls();
				if (controls == null) {
					throw new Exception("Response controls not found!");
				}

				for (Control control : controls) {
					if (control instanceof PagedResultsResponseControl) {
						PagedResultsResponseControl pagedResultsResponseControl = (PagedResultsResponseControl) control;

						cookie = pagedResultsResponseControl.getCookie();
						int lastContentCount = pagedResultsResponseControl
								.getResultSize();
						// Check if content count is constant
						if (contentCount != lastContentCount) {
							log.debug(this + ": contentCount changed from "
									+ contentCount + " to " + lastContentCount);
							contentCount = lastContentCount;
						}
					} else if (control instanceof SortResponseControl) {
						SortResponseControl sortResponseControl = (SortResponseControl) control;
						if (!sortResponseControl.isSorted()) {
							throw new Exception(
									"Error sorting search results! ResultCode="
											+ sortResponseControl
													.getResultCode());
						}
					}
				}
			}

			return new SearchResultEnumeration(ctx, searchRes, objClass,
					targetAttributes, targetNestedObjs, objIsInSearchResult());
		} finally {
			// Remove PagedResultsControl from context
			LDAPUtilities.removeRequestControlFromContext(ctx,
					pagedResultsControl);
		}
	}

	public void close() {
		synchronized (lock) {
			// Abandon sequence of paged-results
			SearchResultEnumeration sre = null;
			try {
				// Set contentsForPage to 0
				contentsForPage = 0;

				sre = getNextPageResultsEnumeration();
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				if (sre != null) {
					sre.close();
				}

				cache.clear();

				try {
					ctx.close();
				} catch (NamingException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}

	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}

	abstract NamingEnumeration<SearchResult> search() throws NamingException;

	abstract boolean objIsInSearchResult();

	@Override
	public Pattern getDataSerializablePattern() {
		return null;
	}

	@Override
	public String read() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);
			oos.close();
			return new String(Base64.encode(baos.toByteArray()));
		} catch (Exception e) {
			throw new RuntimeException("Error reading a "
					+ this.getClass().getSimpleName() + ": " + e.getMessage(),
					e);
		}
	}

	@Override
	public void write(String value) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(
					Base64.decode(value));
			ObjectInputStream ois = new ObjectInputStream(bais);
			copyFromOtherInstance((SimplePagedResults<?>) ois.readObject());
			ois.close();
		} catch (Exception e) {
			throw new RuntimeException("Error writing a "
					+ this.getClass().getSimpleName() + ": " + e.getMessage(),
					e);
		}
	}

	protected void copyFromOtherInstance(SimplePagedResults<?> spr) {
		ctx = spr.ctx;
		startPointDN = spr.startPointDN;
		objClass = spr.objClass;
		attrsToReturn = spr.attrsToReturn;
		targetAttributes = spr.targetAttributes;
		targetNestedObjs = spr.targetNestedObjs;
		contentsForPage = spr.contentsForPage;
		contentCount = spr.contentCount;
		cookie = spr.cookie;
		currentPage = spr.currentPage;
	}
}
