package com.ldapper.context.control;

import java.io.IOException;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.ControlFactory;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.SortResponseControl;

import org.apache.log4j.Logger;

import com.sun.jndi.ldap.EntryChangeResponseControl;

public class ResponseControlFactory extends ControlFactory {

	private static final Logger log = Logger.getLogger(ResponseControlFactory.class);

	public ResponseControlFactory() {}

	@Override
	public Control getControlInstance(Control ctl) throws NamingException {
		String oid = ctl.getID();

		// See if it's one of ours
		if (oid.equals(SortResponseControl.OID)) {
			try {
				return new SortResponseControl(oid, ctl.isCritical(),
						ctl.getEncodedValue());
			}
			catch (IOException e) {
				log.error(e.getMessage(), e);
				throw new NamingException("Error decoding a SortResponseControl: "
						+ e.getMessage());
			}
		} else if (oid.equals(VLVResponseControl.OID_VLV_RESPONSE_CONTROL)) {
			try {
				return new VLVResponseControl(ctl.isCritical(),
						ctl.getEncodedValue());
			}
			catch (Exception e) {
				log.error(e.getMessage(), e);
				throw new NamingException("Error decoding a VLVResponseControl: "
						+ e.getMessage());
			}
		} else if (oid.equals(PagedResultsResponseControl.OID)) {
			try {
				return new PagedResultsResponseControl(oid, ctl.isCritical(),
						ctl.getEncodedValue());
			}
			catch (IOException e) {
				log.error(e.getMessage(), e);
				throw new NamingException(
						"Error decoding a PagedResultsResponseControl: "
								+ e.getMessage());
			}
		} else if (oid.equals(EntryChangeResponseControl.OID)) {
			try {
				return new EntryChangeResponseControl(oid, ctl.isCritical(),
						ctl.getEncodedValue());
			}
			catch (IOException e) {
				log.error(e.getMessage(), e);
				throw new NamingException(
						"Error decoding a EntryChangeResponseControl: "
								+ e.getMessage());
			}
		}

		// Not one of ours; return null and let someone else try
		return null;
	}
}
