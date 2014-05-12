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
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortResponseControl;

import org.apache.log4j.Logger;

import com.ldapper.context.LDAPPERContext;
import com.ldapper.context.control.VLVRequestControl;
import com.ldapper.context.control.VLVResponseControl;
import com.ldapper.context.template.ExternalObjTemplate;
import com.ldapper.data.LDAPPERBean;
import com.ldapper.data.LDAPPERTargetAttribute;
import com.ldapper.data.extension.DataSerializableAsString;
import com.ldapper.utility.LDAPUtilities;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

public abstract class PagedResults<T extends LDAPPERBean> implements
		Serializable, DataSerializableAsString {

	private static final long serialVersionUID = -1744722060896791709L;

	private static final Logger log = Logger.getLogger(PagedResults.class);

	private LdapContext ctx;

	String startPointDN;
	Class<? extends LDAPPERBean> objClass;
	String[] attrsToReturn;
	LDAPPERTargetAttribute[] targetAttributes;
	String[] targetNestedObjs;
	int contentsForPage;
	boolean returningObj;

	private int contentCount = 0;
	private String contextID = null;

	// Result cache
	private transient HashMap<Integer, ArrayList<?>> cache = new HashMap<Integer, ArrayList<?>>();

	private transient Object lock = new Object();

	void init(LdapContext context, String startPointDN,
			Class<? extends LDAPPERBean> objClass,
			LDAPPERTargetAttribute[] targetAttributes,
			String[] targetNestedObjs, boolean returningObj,
			int contentsForPage, String... sortBy) throws Exception {
		this.ctx = context.newInstance(context.getRequestControls());
		this.objClass = objClass;
		this.targetAttributes = targetAttributes;
		this.targetNestedObjs = targetNestedObjs;

		ExternalObjTemplate eot = ExternalObjTemplate.getInstance(objClass);
		this.returningObj = returningObj && eot.getObjTemplate() != null;

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

		this.startPointDN = startPointDN;
		if (this.startPointDN == null) {
			this.startPointDN = "";
		}

		// Get attributes to return
		List<String> attrsToReturnList = eot
				.checkTargetAttributes(targetAttributes);
		attrsToReturn = attrsToReturnList == null ? null : attrsToReturnList
				.toArray(new String[attrsToReturnList.size()]);

		// Create mandatory SortControl and add this to context
		SortControl sortControl = LDAPUtilities.getSortControl(eot, sortBy);
		LDAPUtilities.addRequestControlToContext(ctx, sortControl);
	}

	public void close() {
		synchronized (lock) {
			cache.clear();

			try {
				ctx.close();
			} catch (NamingException e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	@Override
	protected void finalize() throws Throwable {
		close();
	}

	public int getContentsForPage() {
		return contentsForPage;
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
		synchronized (lock) {
			if (pageIndex < 1) {
				throw new Exception("Invalid page index. It must be > 0");
			} else if (pageIndex > 1 && pageIndex > getPageCount()) {
				throw new Exception("Invalid page index. It must be > 0 and < "
						+ getPageCount());
			}

			ArrayList<T> res = (ArrayList<T>) cache.get(pageIndex);
			if (res == null) {
				res = searchPageResults(pageIndex);
			}
			return res;
		}
	}

	@SuppressWarnings("unchecked")
	protected ArrayList<T> searchPageResults(int pageIndex) throws Exception {
		SearchResultEnumeration sre = null;
		try {
			sre = getPageResultsEnumeration(pageIndex);
			ArrayList<T> res = (ArrayList<T>) sre.getAllSearchResults();
			cache.put(pageIndex, res);
			return res;
		} finally {
			if (sre != null) {
				sre.close();
			}
		}
	}

	private SearchResultEnumeration getPageResultsEnumeration(int pageIndex)
			throws Exception {
		// Create VLVRequestControl and add this to context
		VLVRequestControl vlvRequestControl = new VLVRequestControl(0,
				contentsForPage - 1, pageIndex * contentsForPage
						- (contentsForPage - 1), contentCount, contextID);
		LDAPUtilities.addRequestControlToContext(ctx, vlvRequestControl);

		try {
			NamingEnumeration<SearchResult> searchRes = search(ctx);

			// Examine the paged results control response
			Control[] controls = ctx.getResponseControls();
			if (controls == null) {
				throw new Exception("Response controls not found!");
			}

			for (Control control : controls) {
				if (control instanceof VLVResponseControl) {
					VLVResponseControl vlvResponseControl = (VLVResponseControl) control;
					if (!vlvResponseControl.isSuccess()) {
						throw new Exception(
								"Error paging search results! ResultCode="
										+ vlvResponseControl.getVLVResultCode());
					}
					int lastContentCount = vlvResponseControl.getContentCount();

					// Check if content count is constant
					if (contextID != null && contentCount != lastContentCount) {
						synchronized (lock) {
							log.debug(this + ": contentCount changed from "
									+ contentCount + " to " + lastContentCount);
							cache.clear();
						}
					}
					contentCount = lastContentCount;
					contextID = vlvResponseControl.getContextID();
				} else if (control instanceof SortResponseControl) {
					SortResponseControl sortResponseControl = (SortResponseControl) control;
					if (!sortResponseControl.isSorted()) {
						throw new Exception(
								"Error sorting search results! ResultCode="
										+ sortResponseControl.getResultCode());
					}
				}
			}

			return new SearchResultEnumeration(ctx, searchRes, objClass,
					targetAttributes, targetNestedObjs, returningObj);
		} finally {
			// Remove used VLVRequestControl from context
			LDAPUtilities.removeRequestControlFromContext(ctx,
					vlvRequestControl);
		}
	}

	abstract NamingEnumeration<SearchResult> search(LdapContext ctx)
			throws NamingException;

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
			copyFromOtherInstance((PagedResults<?>) ois.readObject());
			ois.close();
		} catch (Exception e) {
			throw new RuntimeException("Error writing a "
					+ this.getClass().getSimpleName() + ": " + e.getMessage(),
					e);
		}
	}

	protected void copyFromOtherInstance(PagedResults<?> pr) {
		ctx = pr.ctx;
		startPointDN = pr.startPointDN;
		objClass = pr.objClass;
		attrsToReturn = pr.attrsToReturn;
		targetAttributes = pr.targetAttributes;
		targetNestedObjs = pr.targetNestedObjs;
		returningObj = pr.returningObj;
		contentsForPage = pr.contentsForPage;
		contentCount = pr.contentCount;
		contextID = pr.contextID;
	}

}
