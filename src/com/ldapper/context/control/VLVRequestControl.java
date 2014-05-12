package com.ldapper.context.control;

import javax.naming.ldap.BasicControl;

import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerEncoder;


/**
 * This class implements the virtual list view request controls as defined in
 * draft-ietf-ldapext-ldapv3-vlv.  The ASN.1 description for the control value
 * is:
 * <BR><BR>
 * <PRE>
 * VirtualListViewRequest ::= SEQUENCE {
 *       beforeCount    INTEGER (0..maxInt),
 *       afterCount     INTEGER (0..maxInt),
 *       target       CHOICE {
 *                      byOffset        [0] SEQUENCE {
 *                           offset          INTEGER (1 .. maxInt),
 *                           contentCount    INTEGER (0 .. maxInt) },
 *                      greaterThanOrEqual [1] AssertionValue },
 *       contextID     OCTET STRING OPTIONAL }
 * </PRE>
 */
public class VLVRequestControl extends BasicControl
{

	private static final long serialVersionUID = -3864215213558598804L;

	/**
	 * The OID for the virtual list view request control.
	 */
	public static final String OID_VLV_REQUEST_CONTROL = "2.16.840.1.113730.3.4.9";
	
	/**
	 * The BER type to use when encoding the byOffset target element.
	 */
	public static final byte TYPE_TARGET_BYOFFSET = (byte) 0xA0;

	/**
	 * The BER type to use when encoding the greaterThanOrEqual target element.
	 */
	public static final byte TYPE_TARGET_GREATERTHANOREQUAL = (byte) 0x81;

	
	/**
	 * Creates a new VLV request control with the provided information.
	 *
	 * @param  beforeCount   The number of entries before the target offset to
	 *                       retrieve in the results page.
	 * @param  afterCount    The number of entries after the target offset to
	 *                       retrieve in the results page.
	 * @param  offset        The offset in the result set to target for the
	 *                       beginning of the page of results.
	 * @param  contentCount  The content count returned by the server in the last
	 *                       phase of the VLV request, or zero for a new VLV
	 *                       request session.
	 * @param  contextID     The context ID provided by the server in the last
	 *                       VLV response for the same set of criteria, or
	 *                       {@code null} if there was no previous VLV response or
	 *                       the server did not include a context ID in the
	 *                       last response.
	 */
	public VLVRequestControl(int beforeCount, int afterCount, 
			int offset, int contentCount, String contextID) throws Exception 
	{
		super(OID_VLV_REQUEST_CONTROL, true, null);
		super.value = getEncodedValue(beforeCount, afterCount, 
				offset, contentCount, contextID);
	}
	
	/**
	 * Creates a new VLV request control with the provided information.
	 *
	 * @param  beforeCount         The number of entries before the target
	 *                             assertion value.
	 * @param  afterCount          The number of entries after the target
	 *                             assertion value.
	 * @param  greaterThanOrEqual  The greaterThanOrEqual target assertion value
	 *                             that indicates where to start the page of
	 *                             results.
	 * @param  contextID           The context ID provided by the server in the
	 *                             last VLV response for the same set of criteria,
	 *                             or {@code null} if there was no previous VLV
	 *                             response or the server did not include a
	 *                             context ID in the last response.
	 */
	public VLVRequestControl(int beforeCount, int afterCount, 
			String greaterThanOrEqual, String contextID) throws Exception
	{
		super(OID_VLV_REQUEST_CONTROL, true, null);
		super.value = getEncodedValue(beforeCount, afterCount,
				greaterThanOrEqual, contextID);
	}

	private byte[] getEncodedValue(int beforeCount, int afterCount,
			int offset, int contentCount, String contextID) throws Exception
	{
		if (beforeCount < 0) {
			throw new Exception("Param beforeCount=" +beforeCount + " is invalid. It must be >= 0");
		}
		if (afterCount < 0) {
			throw new Exception("Param afterCount=" + afterCount + " is invalid. It must be >= 0");
		}
		
		BerEncoder ber = new BerEncoder();
		ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
		ber.encodeInt(beforeCount);
		ber.encodeInt(afterCount);
		encodeTarget(ber, offset, contentCount);
		if (contextID != null && contextID.trim().length()>0) {
			ber.encodeString(contextID, true);	
		}
		ber.endSeq();
		return ber.getTrimmedBuf();
	}

	
	private byte[] getEncodedValue(int beforeCount, int afterCount, 
			String greaterThanOrEqual, String contextID) throws Exception
	{
		if (beforeCount < 0) {
			throw new Exception("Param beforeCount=" +beforeCount + " is invalid. It must be >= 0");
		}
		if (afterCount < 0) {
			throw new Exception("Param afterCount=" + afterCount + " is invalid. It must be >= 0");
		}
		
		BerEncoder ber = new BerEncoder();
		ber.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
		ber.encodeInt(beforeCount);
		ber.encodeInt(afterCount);
		encodeTarget(ber, greaterThanOrEqual);
		if (contextID != null && contextID.trim().length()>0) {
			ber.encodeString(contextID, true);	
		}
		ber.endSeq();
		return ber.getTrimmedBuf();
	}

	private void encodeTarget(BerEncoder ber, int offset, int contentCount) throws Exception {
		if (offset <= 0) {
			throw new Exception("Param offset=" + offset + " is invalid. It must be > 0");
		}
		if (contentCount < 0) {
			throw new Exception("Param contentCount=" + contentCount + " is invalid. It must be >= 0");
		}

		ber.beginSeq(160); //TYPE_TARGET_BYOFFSET
		ber.encodeInt(offset);
		ber.encodeInt(contentCount);
		ber.endSeq();
	}

	private void encodeTarget(BerEncoder ber, String greaterThanOrEqual) throws Exception {
		if (greaterThanOrEqual == null || greaterThanOrEqual.trim().length()==0) {
			throw new Exception("Param greaterThanOrEqual is null or empty.");
		}
		ber.encodeString(greaterThanOrEqual, 129, true); //TYPE_TARGET_GREATERTHANOREQUAL
	}
}

