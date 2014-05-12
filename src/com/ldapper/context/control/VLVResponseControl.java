package com.ldapper.context.control;

import javax.naming.ldap.BasicControl;

import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerDecoder;


/**
 * This class implements the virtual list view response controls as defined in
 * draft-ietf-ldapext-ldapv3-vlv.  The ASN.1 description for the control value
 * is:
 * <BR><BR>
 * <PRE>
 * VirtualListViewResponse ::= SEQUENCE {
 *       targetPosition    INTEGER (0 .. maxInt),
 *       contentCount     INTEGER (0 .. maxInt),
 *       virtualListViewResult ENUMERATED {
 *            success (0),
 *            operationsError (1),
 *            protocolError (3),
 *            unwillingToPerform (53),
 *            insufficientAccessRights (50),
 *            timeLimitExceeded (3),
 *            adminLimitExceeded (11),
 *            innapropriateMatching (18),
 *            sortControlMissing (60),
 *            offsetRangeError (61),
 *            other(80),
 *            ... },
 *       contextID     OCTET STRING OPTIONAL }
 * </PRE>
 */
public class VLVResponseControl extends BasicControl
{

	private static final long serialVersionUID = 47868926719445052L;

	/**
	 * The OID for the virtual list view request control.
	 */
	public static final String OID_VLV_RESPONSE_CONTROL = "2.16.840.1.113730.3.4.10";

	// The context ID for this VLV response control.
	private String contextID;

	// The content count estimating the total number of entries in the result set.
	private int contentCount;

	// The offset of the target entry in the result set.
	private int targetPosition;

	// The result code for the VLV operation.
	private int vlvResultCode;
	
    /**
     * Constructs a VLVResponseControl using the supplied arguments.
     * 
     * @param	criticality	The control's criticality.
     * @param	value		The control's ASN.1 BER encoded value.
     */
	public VLVResponseControl(boolean criticality, byte[] value) throws Exception 
	{
		super(OID_VLV_RESPONSE_CONTROL, true, value);
		decodedValue();
	}
	
	private void decodedValue() throws Exception {
        // decode value
        BerDecoder ber = new BerDecoder(value, 0, value.length);

        ber.parseSeq(null);
        targetPosition = ber.parseInt();
        contentCount = ber.parseInt();
        vlvResultCode = ber.parseEnumeration();
    	if ((ber.bytesLeft() > 0) && (ber.peekByte() == Ber.ASN_CONTEXT)) {
    		contextID = ber.parseString(true);
    	}
	}

	/**
	 * Retrieves the position of the target entry in the result set.
	 *
	 * @return  The position of the target entry in the result set.
	 */
	public int getTargetPosition()
	{
		return targetPosition;
	}

	/**
	 * Retrieves the estimated total number of entries in the result set.
	 *
	 * @return  The estimated total number of entries in the result set.
	 */
	public int getContentCount()
	{
		return contentCount;
	}

	/**
	 * Retrieves the result code for the VLV operation.
	 *
	 * @return  The result code for the VLV operation.
	 */
	public int getVLVResultCode()
	{
		return vlvResultCode;
	}

	/**
	 * Retrieves a context ID value that should be included in the next request
	 * to retrieve a page of the same result set.
	 *
	 * @return  A context ID value that should be included in the next request to
	 *          retrieve a page of the same result set, or {@code null} if there
	 *          is no context ID.
	 */
	public String getContextID()
	{
		return contextID;
	}

	public boolean isSuccess() {
		return vlvResultCode == 0;
	}
}

