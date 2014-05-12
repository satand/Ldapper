package com.ldapper.data;

import java.io.OutputStream;

import org.w3c.dom.Document;

public interface LDAPPERBeanToXML extends LDAPPERBean {

    /**
     * Get object in xml format
     * 
     * @return
     *     XML object format, possible object is {@link String}
     */
	public String toXml();
	
    /**
     * Write object xml format on an OutputStream
     * 
     * @param stream allowed object is {@link OutputStream}
     */
    public void printXml(OutputStream stream);
    
    /**
     * Get object in document format
     * 
     * @return
     *     Document object format, possible object is {@link Document}
     */
    public Document toDocument();

}
