package com.ldapper.data;

import com.ldapper.exception.InvalidDN;

public interface IDN extends IBaseDN {

    /**
     * Get object LDAP DN
     * 
     * @return
     *     possible object is {@link String}
     * @throws Exception
     *     in case of incomplete LDAP DN
     */
	public String getDN() throws InvalidDN;
	
    /**
     * Set object LDAP DN
     * 
     * @param dn object LDAP DN, allowed object is {@link String}
     * @throws Exception
     *     in case of invalid LDAP DN
     */
	public void setDN(String dn) throws InvalidDN;
}
